package com.pthanureddy.scheduling;
import static com.pthanureddy.scheduling.Models.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SchedulingService {
    private final JdbcTemplate db;
    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);
    public SchedulingService(JdbcTemplate db) { this.db = db; }
    private final org.springframework.jdbc.core.RowMapper<Slot> slotMapper = (r,n) -> new Slot(r.getString("id"), r.getString("clinician"), r.getString("market"), r.getString("specialty"), r.getString("language"), r.getObject("starts", java.time.OffsetDateTime.class), r.getObject("ends", java.time.OffsetDateTime.class));
    private final org.springframework.jdbc.core.RowMapper<Booking> bookingMapper = (r,n) -> new Booking(r.getString("id"), r.getString("patient"), r.getString("slot_id"), r.getString("market"), r.getString("specialty"), r.getString("language"), r.getBoolean("active"));
    public List<Slot> available(String market, String specialty, String language) {
        return db.query("SELECT s.* FROM slots s WHERE market=? AND specialty=? AND language=? AND NOT EXISTS (SELECT 1 FROM bookings b JOIN slots taken ON taken.id=b.slot_id WHERE b.active AND taken.clinician=s.clinician AND taken.starts<s.ends AND taken.ends>s.starts) ORDER BY starts,id", slotMapper, market,specialty,language);
    }
    public List<Booking> bookings() { return db.query("SELECT * FROM bookings ORDER BY id", bookingMapper); }
    private void lock() { db.queryForObject("SELECT id FROM mutation_lock WHERE id=1 FOR UPDATE", Integer.class); }
    private Slot slot(String id) {
        var rows = db.query("SELECT * FROM slots WHERE id=?", slotMapper,id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Slot not found");
        return rows.get(0);
    }
    private Booking booking(String id) {
        var rows=db.query("SELECT * FROM bookings WHERE id=?",bookingMapper,id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Booking not found");
        return rows.get(0);
    }
    private Booking replay(String key, String fingerprint) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,60}")) throw error(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");
        var rows=db.query("SELECT fingerprint,booking_id FROM operations WHERE op_key=?",(r,n)->new String[]{r.getString(1),r.getString(2)},key);
        if(rows.isEmpty()) return null;
        if(!fingerprint.equals(rows.get(0)[0])) throw error(HttpStatus.CONFLICT,"Idempotency key reused with changed request");
        return booking(rows.get(0)[1]);
    }
    private void record(String key, String fingerprint, String id) {
        db.update("INSERT INTO operations VALUES (?,?,?)",key,fingerprint,id);
        log.info("Scheduling mutation recorded in transaction: booking={}",id);
    }
    private void check(Slot target, Need need, String excluded) {
        if(!target.market().equals(need.market()) || !target.specialty().equals(need.specialty()) || !target.language().equals(need.language()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Clinician does not match market, specialty and language");
        int conflicts=db.queryForObject("SELECT COUNT(*) FROM bookings b JOIN slots s ON b.slot_id=s.id WHERE b.active AND b.id<>? AND s.starts<? AND s.ends>? AND (s.clinician=? OR b.patient=?)",Integer.class,excluded,target.ends(),target.starts(),target.clinician(),need.patient());
        if(conflicts>0) throw error(HttpStatus.CONFLICT,"Clinician or patient has an overlapping booking");
    }
    @Transactional
    public Booking book(String key, BookRequest request) {
        lock(); String fingerprint="BOOK:"+request.toString();
        Booking prior=replay(key,fingerprint); if(prior!=null) return prior;
        Slot target=slot(request.slot()); check(target,request.need(),"");
        String id=UUID.randomUUID().toString(); Need n=request.need();
        db.update("INSERT INTO bookings VALUES (?,?,?,?,?,?,true)",id,n.patient(),target.id(),n.market(),n.specialty(),n.language());
        record(key,fingerprint,id); return booking(id);
    }
    @Transactional
    public Booking rebook(String key,String id, RebookRequest request) {
        lock(); String fingerprint="REBOOK:"+id+":"+request.slot();
        Booking prior=replay(key,fingerprint); if(prior!=null) return prior;
        Booking old=booking(id); if(!old.active()) throw error(HttpStatus.CONFLICT,"Booking is cancelled");
        Slot target=slot(request.slot()); check(target,new Need(old.patient(),old.market(),old.specialty(),old.language()),id);
        db.update("UPDATE bookings SET slot_id=? WHERE id=?",target.id(),id);
        record(key,fingerprint,id); return booking(id);
    }
    @Transactional
    public Booking cancel(String key,String id) {
        lock(); String fingerprint="CANCEL:"+id;
        Booking prior=replay(key,fingerprint); if(prior!=null) return prior;
        booking(id); db.update("UPDATE bookings SET active=false WHERE id=?",id);
        record(key,fingerprint,id); return booking(id);
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
