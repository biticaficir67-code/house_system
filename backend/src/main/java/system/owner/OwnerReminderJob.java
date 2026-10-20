package system.owner;

import static system.common.DataStore.*;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import system.common.DataStore;

import java.time.LocalDate;

@Component
public class OwnerReminderJob {
    private final DataStore db;

    public OwnerReminderJob(DataStore db) {
        this.db = db;
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void refresh() {
        db.execute(
                "UPDATE owner_entrustment SET status='EXPIRED' WHERE status='ACTIVE' AND"
                    + " end_date<#{p.today} AND deleted=0",
                args("today", LocalDate.now()));
        for (var row :
                db.list(
                        "SELECT id FROM owner_entrustment WHERE status IN ('ACTIVE','EXPIRED') AND"
                            + " end_date<=#{p.end} AND deleted=0",
                        args("end", LocalDate.now().plusDays(7)))) refreshOne(id(row, "id"));
    }

    public void refreshOne(long entrustment) {
        var row =
                db.one(
                        "SELECT * FROM owner_entrustment WHERE id=#{p.id} FOR UPDATE",
                        args("id", entrustment));
        LocalDate end = (LocalDate) row.get("endDate");
        if (end.isAfter(LocalDate.now().plusDays(7)) || "CANCELLED".equals(row.get("status")))
            return;
        var old =
                db.optional(
                        "SELECT * FROM owner_reminder WHERE entrustment_id=#{p.id}",
                        args("id", entrustment));
        if (old.isEmpty())
            db.insert(
                    "owner_reminder",
                    args("entrustment_id", entrustment, "due_date", end, "read_status", "UNREAD"));
        else if (!end.equals(old.get().get("dueDate")))
            db.update(
                    "owner_reminder",
                    id(old.get(), "id"),
                    args("due_date", end, "read_status", "UNREAD"));
    }
}
