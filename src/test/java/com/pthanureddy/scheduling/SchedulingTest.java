package com.pthanureddy.scheduling;
import static com.pthanureddy.scheduling.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;
@SpringBootTest
@AutoConfigureMockMvc
class SchedulingTest {
    @Autowired SchedulingService service;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    Need need(String patient) { return new Need(patient,"SE","GP","en"); }
    BookRequest request(String patient,String slot) { return new BookRequest(slot,need(patient)); }
    @BeforeEach void reset() {
        db.update("DELETE FROM operations"); db.update("DELETE FROM bookings");
        db.update("DELETE FROM slots WHERE id NOT IN ('se-1','se-2','no-1','fr-1','uk-1')");
    }
    @Test void filtersMarketsAndLanguage() {
        assertEquals(2,service.available("SE","GP","en").size());
        assertEquals(1,service.available("FR","GP","fr").size());
        assertTrue(service.available("FR","GP","en").isEmpty());
    }
    @Test void bookingRemovesAvailability() {
        service.book("one",request("patient-a","se-1"));
        assertEquals("se-2",service.available("SE","GP","en").get(0).id());
    }
    @Test void bookingReplayDoesNotDuplicate() {
        var a=service.book("one",request("patient-a","se-1"));
        assertEquals(a,service.book("one",request("patient-a","se-1")));
        assertEquals(1,service.bookings().size());
    }
    @Test void changedReplayConflicts() {
        service.book("one",request("patient-a","se-1"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.book("one",request("patient-b","se-1"))).getStatusCode().value());
    }
    @Test void rejectsMarketMismatch() {
        assertEquals(422,assertThrows(ResponseStatusException.class,()->service.book("one",request("patient-a","no-1"))).getStatusCode().value());
    }
    @Test void rejectsSpecialtyAndLanguageMismatch() {
        assertThrows(ResponseStatusException.class,()->service.book("one",new BookRequest("se-1",new Need("a","SE","PSYCHOLOGY","en"))));
        assertThrows(ResponseStatusException.class,()->service.book("two",new BookRequest("se-1",new Need("a","SE","GP","sv"))));
    }
    @Test void rebookingReleasesOldSlot() {
        var a=service.book("one",request("patient-a","se-1"));
        var moved=service.rebook("move",a.id(),new RebookRequest("se-2"));
        assertEquals("se-2",moved.slot()); assertEquals("se-1",service.available("SE","GP","en").get(0).id());
        assertEquals(moved,service.rebook("move",a.id(),new RebookRequest("se-2")));
    }
    @Test void failedRebookingPreservesOriginal() {
        var a=service.book("one",request("patient-a","se-1"));
        service.book("two",request("patient-b","se-2"));
        assertThrows(ResponseStatusException.class,()->service.rebook("move",a.id(),new RebookRequest("se-2")));
        assertEquals("se-1",service.bookings().stream().filter(b->b.id().equals(a.id())).findFirst().orElseThrow().slot());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM operations",Integer.class));
    }
    @Test void cancellationRestoresCapacity() {
        var a=service.book("one",request("patient-a","se-1"));
        assertFalse(service.cancel("cancel",a.id()).active());
        assertFalse(service.cancel("cancel",a.id()).active());
        assertEquals(2,service.available("SE","GP","en").size());
        assertThrows(ResponseStatusException.class,()->service.rebook("move",a.id(),new RebookRequest("se-2")));
    }
    @Test void overlappingDifferentSlotIsRejected() {
        db.update("INSERT INTO slots SELECT 'overlap',clinician,market,specialty,language,starts,ends FROM slots WHERE id='se-1'");
        service.book("one",request("patient-a","se-1"));
        assertThrows(ResponseStatusException.class,()->service.book("two",request("patient-b","overlap")));
        assertTrue(service.available("SE","GP","en").stream().noneMatch(s->s.id().equals("overlap")));
    }
    @Test void patientCannotBookConcurrentDifferentClinicians() {
        db.update("INSERT INTO slots SELECT 'other','other-clinician',market,specialty,language,starts,ends FROM slots WHERE id='se-1'");
        service.book("one",request("patient-a","se-1"));
        assertThrows(ResponseStatusException.class,()->service.book("two",request("patient-a","other")));
    }
    @Test void adjacentAppointmentsAreAllowed() {
        service.book("one",request("patient-a","se-1"));
        service.book("two",request("patient-a","se-2"));
        assertEquals(2,service.bookings().size());
    }
    @Test void twoConcurrentRequestsHaveOneWinner() throws Exception {
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->attempt(start,"a")); var b=executor.submit(()->attempt(start,"b"));
            start.countDown(); assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
            assertEquals(1,service.bookings().size());
        } finally { executor.shutdownNow(); }
    }
    int attempt(CountDownLatch start,String patient) throws Exception {
        start.await(); try { service.book(patient,request(patient,"se-1")); return 1; }
        catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); return 0; }
    }
    @Test void missingSlotAndInvalidKeyAreRejected() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.book("one",request("a","missing"))).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.book("bad key",request("a","se-1"))).getStatusCode().value());
    }
    @Test void httpRejectsInvalidInput() throws Exception {
        http.perform(post("/api/bookings").header("Idempotency-Key","one").contentType("application/json").content("{\"slot\":\"se-1\",\"need\":{\"patient\":\"\",\"market\":\"XX\",\"specialty\":\"GP\",\"language\":\"en\"}}" )).andExpect(status().isBadRequest());
    }
    @Test void httpBookingAndConflictContract() throws Exception {
        String payload="{\"slot\":\"se-1\",\"need\":{\"patient\":\"patient-a\",\"market\":\"SE\",\"specialty\":\"GP\",\"language\":\"en\"}}";
        http.perform(post("/api/bookings").header("Idempotency-Key","one").contentType("application/json").content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        http.perform(post("/api/bookings").header("Idempotency-Key","two").contentType("application/json").content(payload)).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
    }
}
