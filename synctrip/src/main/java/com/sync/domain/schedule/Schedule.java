package com.sync.domain.schedule;

import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "schedules")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "is_free_time", nullable = false)
    private boolean freeTime = false;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "travel_time_from_prev")
    private Integer travelTimeFromPrev;

    // 알고리즘 경고 플래그 (생성 시점 값 보존, 재계산 시 갱신)
    @Column(name = "is_outlier_candidate", nullable = false)
    private boolean outlierCandidate = false;

    @Column(name = "opening_hours_violation", nullable = false)
    private boolean openingHoursViolation = false;

    @Column(name = "meal_window_violation", nullable = false)
    private boolean mealWindowViolation = false;

    @Column(name = "late_schedule", nullable = false)
    private boolean lateSchedule = false;

    @Column(name = "opening_hours_unverified", nullable = false)
    private boolean openingHoursUnverified = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Schedule() {}

    private Schedule(Band band, Place place, int dayNumber, int slotOrder,
                     LocalTime startTime, int durationMinutes, Integer travelTimeFromPrev,
                     boolean outlierCandidate, boolean openingHoursViolation,
                     boolean mealWindowViolation, boolean lateSchedule, boolean openingHoursUnverified) {
        this.band = band;
        this.place = place;
        this.dayNumber = dayNumber;
        this.slotOrder = slotOrder;
        this.freeTime = false;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.travelTimeFromPrev = travelTimeFromPrev;
        this.outlierCandidate = outlierCandidate;
        this.openingHoursViolation = openingHoursViolation;
        this.mealWindowViolation = mealWindowViolation;
        this.lateSchedule = lateSchedule;
        this.openingHoursUnverified = openingHoursUnverified;
    }

    public static Schedule create(Band band, Place place, int dayNumber, int slotOrder,
                                  LocalTime startTime, int durationMinutes, Integer travelTimeFromPrev,
                                  boolean outlierCandidate, boolean openingHoursViolation,
                                  boolean mealWindowViolation, boolean lateSchedule, boolean openingHoursUnverified) {
        return new Schedule(band, place, dayNumber, slotOrder, startTime, durationMinutes, travelTimeFromPrev,
                outlierCandidate, openingHoursViolation, mealWindowViolation, lateSchedule, openingHoursUnverified);
    }

    /** 재계산 시 위반 플래그 갱신 — outlier는 K-Means 재실행 안 하므로 호출 측에서 보존값 전달 */
    public void updateFlags(boolean outlierCandidate, boolean openingHoursViolation,
                             boolean mealWindowViolation, boolean lateSchedule, boolean openingHoursUnverified) {
        this.outlierCandidate = outlierCandidate;
        this.openingHoursViolation = openingHoursViolation;
        this.mealWindowViolation = mealWindowViolation;
        this.lateSchedule = lateSchedule;
        this.openingHoursUnverified = openingHoursUnverified;
    }

    public void updatePlace(Place newPlace) {
        this.place = newPlace;
        this.durationMinutes = newPlace.getEstimatedDuration();
    }

    public void updateTimes(int newSlotOrder, LocalTime newStartTime,
                            int newDuration, Integer newTravelTimeFromPrev) {
        this.slotOrder = newSlotOrder;
        this.startTime = newStartTime;
        this.durationMinutes = newDuration;
        this.travelTimeFromPrev = newTravelTimeFromPrev;
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public Place getPlace() { return place; }
    public int getDayNumber() { return dayNumber; }
    public int getSlotOrder() { return slotOrder; }
    public boolean isFreeTime() { return freeTime; }
    public LocalTime getStartTime() { return startTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Integer getTravelTimeFromPrev() { return travelTimeFromPrev; }
    public boolean isOutlierCandidate() { return outlierCandidate; }
    public boolean isOpeningHoursViolation() { return openingHoursViolation; }
    public boolean isMealWindowViolation() { return mealWindowViolation; }
    public boolean isLateSchedule() { return lateSchedule; }
    public boolean isOpeningHoursUnverified() { return openingHoursUnverified; }
}
