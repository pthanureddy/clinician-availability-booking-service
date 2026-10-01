package com.pthanureddy.scheduling;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;
public final class Models {
    private Models() {}
    public record Need(@NotBlank @Size(max=60) @Pattern(regexp="[A-Za-z0-9_-]+") String patient,
                       @NotNull @Pattern(regexp="SE|NO|FR|UK") String market,
                       @NotNull @Pattern(regexp="GP|PSYCHOLOGY") String specialty,
                       @NotNull @Pattern(regexp="en|sv|no|fr") String language) {}
    public record BookRequest(@NotBlank @Size(max=40) String slot, @NotNull @jakarta.validation.Valid Need need) {}
    public record RebookRequest(@NotBlank @Size(max=40) String slot) {}
    public record Slot(String id, String clinician, String market, String specialty, String language,
                       OffsetDateTime starts, OffsetDateTime ends) {}
    public record Booking(String id, String patient, String slot, String market, String specialty, String language, boolean active) {}
}
