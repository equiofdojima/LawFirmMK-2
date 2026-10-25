import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * The records of the firm.
 *
 * Inheritance:            Entity
 *                          |-- Person ---- Client, User
 *                          |-- Case, Payment
 *                          `-- CaseRecord -- Appointment, Invoice, Doc
 *
 * Encapsulation: every field is private. Other classes read it with a getter and change it with a
 * setter, so a record can clean up or refuse a value before it is stored.
 *
 * (These are top-level classes without "public": they are visible to the whole package by name.)
 */

/** Anything that is stored by the firm: it has an id and a printable code such as C-0001. */
// >>> OOP | ABSTRACTION: Abstract base class: it cannot be created with "new". It holds what EVERY record has (an id and a code).
abstract class Entity {
    // >>> OOP | ENCAPSULATION: private + final: the id can only be read (getId()) and can never be changed from outside.
    private final int id;

    protected Entity(int id) { this.id = id; }

    public int getId() { return id; }

    /** The code people see (C-0001, INV-0002, ...). Each kind of record builds its own. */
    // >>> OOP | ABSTRACTION: Abstract method: no body here. Every subclass MUST write its own version (see the @Override code() methods).
    public abstract String code();

    @Override public String toString() { return code(); }

    /** Text fields never hold null and never keep stray spaces. */
    protected static String clean(String s) { return s == null ? "" : s.trim(); }
}

/** A human being the firm keeps a record of: has a name and contact details. */
// >>> OOP | INHERITANCE: Person IS-A Entity: it inherits id, getId() and code(), and adds name, email and phone.
abstract class Person extends Entity {
    private String name;
    private String email;
    private String phone;

    protected Person(int id, String name, String email, String phone) {
        super(id);
        setName(name);
        setEmail(email);
        setPhone(phone);
    }

    public String getName() { return name; }
    // >>> OOP | ENCAPSULATION: The field is private; the setter cleans the value (trims it, never stores null) before it is kept.
    public void setName(String name) { this.name = clean(name); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = clean(email); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = clean(phone); }

    @Override public String toString() { return name; }
}

/** A person the firm represents. Clients are only records: they do not sign in. */
// >>> OOP | INHERITANCE: Client IS-A Person (and so an Entity): name, email and phone are not written again here.
class Client extends Person {
    Client(int id, String name, String email, String phone) {
        super(id, name, email, phone);
    }

    // >>> OOP | POLYMORPHISM: Overriding: same method name as Entity.code(), different result for each kind of record (CL-001, USR-001, INV-0001 ...).
    @Override public String code() { return String.format("CL-%03d", getId()); }
}

/** A staff account (administrator or lawyer) that can sign in. */
// >>> OOP | INHERITANCE: User IS-A Person too: a second subclass that reuses the same name/email/phone code.
class User extends Person {
    public static final String ADMIN = "ADMIN", LAWYER = "LAWYER";

    /** Wrong passwords allowed in a row, and how long an administrator is locked out afterwards. */
    public static final int MAX_ATTEMPTS = 3;
    public static final int ADMIN_LOCK_SECONDS = 30;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,}$");

    // what each non-admin role may do (admins may do everything)
    private static final String[] LAWYER_PERMS = {
        "VIEW_DASHBOARD", "VIEW_CASES", "ADD_CASE", "EDIT_CASE", "UPDATE_STATUS", "VIEW_CLIENTS", "EDIT_CLIENT",
        "VIEW_SCHEDULE", "EDIT_APPOINTMENT", "VIEW_DOCUMENTS", "ADD_DOCUMENT", "REMOVE_DOCUMENT",
        "VIEW_BILLING", "ADD_INVOICE", "EDIT_INVOICE", "RECORD_PAYMENT", "VIEW_REPORTS"
    };

    // >>> OOP | ENCAPSULATION: Private fields. There is no setter for the username, and the password hash has no way to be read as text.
    private final String username;
    private String role;
    private String passwordHash;

    // three-tries rule
    private int failedAttempts = 0;
    private boolean blocked = false;      // saved: stays until an administrator unblocks the account
    private long lockedUntil = 0;         // not saved: short lock-out used for administrators only

    User(int id, String username, String fullName, String email, String phone, String role) {
        super(id, fullName, email, phone);
        this.username = clean(username);
        this.role = role;
    }

    @Override public String code() { return String.format("USR-%03d", getId()); }

    // ---- accessors ----
    public String getUsername() { return username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    /** An account can be used while it is not blocked (Block / Unblock is the only switch). */
    public boolean isActive() { return !blocked; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String saved) { this.passwordHash = saved; }


    // ---- validation rules used by the admin's Add User form ----
    public static boolean isValidEmail(String s) { return s != null && EMAIL.matcher(s).matches(); }

    public static boolean isValidUsername(String s) { return s != null && USERNAME.matcher(s).matches(); }

    // ---- permissions ----
    // >>> OOP | ENCAPSULATION: The access-control rules are hidden inside User. Pages only ask "can this user do X?".
    public boolean can(String permission) {
        switch (role) {
            case ADMIN:  return true;
            case LAWYER: return Arrays.asList(LAWYER_PERMS).contains(permission);
            default:     return false;
        }
    }

    // ---- passwords: salt + hash together, saved as one text "salt:hash" ----
    // >>> OOP | ENCAPSULATION: Only the salted hash is stored. Other classes cannot see or change the password directly.
    public void setPassword(String password) {
        byte[] salt = new byte[16];                    // a new random salt for every password
        new SecureRandom().nextBytes(salt);
        passwordHash = hashWithSalt(password, salt);
    }

    public boolean checkPassword(String password) {
        if (passwordHash == null || !passwordHash.contains(":")) return false;
        String savedSalt = passwordHash.split(":")[0];                  // take the salt back out
        byte[] salt = Base64.getDecoder().decode(savedSalt);
        String again = hashWithSalt(password, salt);                    // same salt + typed password
        return java.security.MessageDigest.isEqual(again.getBytes(), passwordHash.getBytes());
    }

    /** Salts and hashes in one step. Returns "salt:hash", both as text (Base64). */
    private static String hashWithSalt(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 60000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- three tries, then blocked ----
    public boolean isBlocked() { return blocked; }

    public int getFailedAttempts() { return failedAttempts; }

    public void setFailedAttempts(int n) { failedAttempts = Math.max(0, n); }

    public int attemptsLeft() { return Math.max(0, MAX_ATTEMPTS - failedAttempts); }

    public void block() { blocked = true; }

    /** Lets the account sign in again and starts counting the wrong tries from zero. */
    public void unblock() {
        blocked = false;
        failedAttempts = 0;
        lockedUntil = 0;
    }

    /** Seconds an administrator still has to wait after 3 wrong tries (0 = not locked). */
    public int lockSecondsLeft() {
        long msLeft = lockedUntil - System.currentTimeMillis();
        if (msLeft <= 0) return 0;
        return (int) Math.ceil(msLeft / 1000.0);
    }

    /**
     * Call this after a wrong password. On the 3rd wrong try a lawyer is blocked until an administrator
     * unblocks the account. An administrator is only locked for a short time (otherwise nobody would be
     * left to unblock anyone). Returns true when this was the 3rd wrong try.
     */
    // >>> OOP | ENCAPSULATION: The three-tries rule lives inside User. Other classes call this method; they cannot change the counter themselves.
    public boolean recordFailure() {
        failedAttempts++;
        if (failedAttempts < MAX_ATTEMPTS) return false;
        if (role.equals(ADMIN)) {
            lockedUntil = System.currentTimeMillis() + ADMIN_LOCK_SECONDS * 1000L;
            failedAttempts = 0;
        } else {
            blocked = true;
        }
        return true;
    }

    /** Call this after a correct password. */
    public void recordSuccess() { failedAttempts = 0; }

    // ---- names ----
    // "Atty. Maria Reyes" -> "Atty. M. Reyes"
    public String shortName() {
        String[] p = getName().split(" ");
        if (p.length >= 3 && p[0].equals("Atty.")) return "Atty. " + p[1].charAt(0) + ". " + p[p.length - 1];
        return getName();
    }

    // "Atty. Maria Reyes" -> "MR"
    public String initials() {
        String first = null, last = null;
        String[] parts = getName().split(" ");
        for (int ix = 0; ix < parts.length; ix++) {
            String p = parts[ix];
            if (p.equals("Atty.") || p.isEmpty()) continue;
            if (first == null) first = p;
            last = p;
        }
        if (first == null) return "?";
        String s = "" + first.charAt(0);
        if (last != null && last != first) s += last.charAt(0);
        return s.toUpperCase();
    }

    public String roleTitle() {
        switch (role) {
            case ADMIN:  return "Administrator";
            case LAWYER: return "Attorney";
            default:     return "Attorney";
        }
    }

    /** What the Status column of the user table shows. */
    public String statusText() {
        if (blocked || lockSecondsLeft() > 0) return "Blocked";
        return "Active";
    }
}

/** A legal matter. */
// >>> OOP | INHERITANCE: Case IS-A Entity. Its fields are private, with getters and setters below.
class Case extends Entity {
    public static final String ACTIVE = "Active", PENDING = "Pending", HANDLED = "Handled";
    public static final String[] STATUSES = {ACTIVE, PENDING, HANDLED};
    public static final String[] TYPES = {"Civil", "Criminal", "Corporate", "Family", "Probate", "Labor", "Property"};

    private static final String FIRM_NAME = "Del Rosario Law Offices";
    private static final String FIRM_SHORT = "Del Rosario";

    private String title;
    private Client client;
    private User lawyer;
    private String type;
    private double fee;
    private String status;
    private String opposing = "";
    private String court = "";
    private LocalDate filed;
    private LocalDate opened;
    private String description = "";

    Case(int id, String title, Client client, User lawyer, String type, double fee, String status) {
        super(id);
        setTitle(title);
        this.client = client;
        this.lawyer = lawyer;
        setType(type);
        setFee(fee);
        setStatus(status);
        this.opened = LocalDate.now();
    }

    public static String getFirmName() { return FIRM_NAME; }
    public static String getFirmShort() { return FIRM_SHORT; }

    @Override public String code() { return String.format("C-%04d", getId()); }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = clean(title); }

    public Client getClient() { return client; }

    public User getLawyer() { return lawyer; }
    public void setLawyer(User lawyer) { this.lawyer = lawyer; }

    public String getType() { return type; }
    public void setType(String type) { this.type = clean(type); }

    public double getFee() { return fee; }
    // >>> OOP | ENCAPSULATION: The setter protects the rule "a fee is never negative".
    public void setFee(double fee) { this.fee = Math.max(0, fee); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = clean(status); }

    public String getOpposing() { return opposing; }
    public void setOpposing(String opposing) { this.opposing = clean(opposing); }

    public String getCourt() { return court; }
    public void setCourt(String court) { this.court = clean(court); }

    public LocalDate getFiled() { return filed; }
    public void setFiled(LocalDate filed) { this.filed = filed; }

    public LocalDate getOpened() { return opened; }
    public void setOpened(LocalDate opened) { this.opened = opened; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = clean(description); }

    /** The data rule of the access control: who may see this case. */
    // >>> OOP | ENCAPSULATION: The data-access rule ("a lawyer sees only own cases") is written once, inside Case.
    public boolean isVisibleTo(User u) {
        if (u == null) return false;
        switch (u.getRole()) {
            case User.ADMIN:  return true;
            case User.LAWYER: return lawyer == u;
            default:          return false;
        }
    }

    @Override public String toString() { return code() + "  " + title; }
}

/** A record that belongs to a case (appointment, invoice, document): it is visible to whoever sees the case. */
// >>> OOP | INHERITANCE: Second level of the hierarchy: Appointment, Invoice and Doc all belong to a case, so that code is written once, here.
abstract class CaseRecord extends Entity {
    private Case relatedCase;

    protected CaseRecord(int id, Case relatedCase) {
        super(id);
        this.relatedCase = relatedCase;
    }

    public Case getRelatedCase() { return relatedCase; }
    public void setRelatedCase(Case relatedCase) { this.relatedCase = relatedCase; }

    /** The assigned lawyer always follows the case. */
    // >>> OOP | COMPOSITION: Association + delegation: a CaseRecord HAS-A Case and asks it for the lawyer, so the lawyer always follows the case.
    public User getLawyer() { return relatedCase.getLawyer(); }

}

// >>> OOP | INHERITANCE: Appointment (like Invoice and Doc) inherits relatedCase and getLawyer() from CaseRecord.
class Appointment extends CaseRecord {
    public static final String CONFIRMED = "Confirmed", TENTATIVE = "Tentative", CANCELLED = "Cancelled";

    private String event;
    private LocalDate date;
    private LocalTime time;
    private String location;
    private String status;

    Appointment(int id, String event, Case relatedCase, LocalDate date, LocalTime time, String location, String status) {
        super(id, relatedCase);
        setEvent(event);
        this.date = date;
        this.time = time;
        setLocation(location);
        setStatus(status);
    }

    @Override public String code() { return String.format("APT-%04d", getId()); }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = clean(event); }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = clean(location); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = clean(status); }

    public LocalDateTime when() { return LocalDateTime.of(date, time); }

    public boolean isUpcoming() {
        return !status.equals(CANCELLED) && !when().isBefore(LocalDateTime.now());
    }
}

class Invoice extends CaseRecord {
    public static final String[] METHODS = {"Cash", "Bank transfer", "Check", "GCash", "Credit card"};

    private String description;
    private LocalDate issued;
    private LocalDate due;
    private double amount;
    // >>> OOP | COMPOSITION: Composition: an Invoice OWNS its Payments (has-a). The list is private.
    private final ArrayList<Payment> payments = new ArrayList<>();

    Invoice(int id, Case relatedCase, String description, LocalDate issued, LocalDate due, double amount) {
        super(id, relatedCase);
        setDescription(description);
        this.issued = issued;
        this.due = due;
        setAmount(amount);
    }

    @Override public String code() { return String.format("INV-%04d", getId()); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = clean(description); }

    public LocalDate getIssued() { return issued; }
    public void setIssued(LocalDate issued) { this.issued = issued; }

    public LocalDate getDue() { return due; }
    public void setDue(LocalDate due) { this.due = due; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = Math.max(0, amount); }

    /** A read-only view: payments are added and removed only through addPayment / removePayment. */
    // >>> OOP | ENCAPSULATION: Returns a read-only view, so other classes cannot add or remove payments behind the invoice's back (use addPayment / removePayment).
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }
    public void addPayment(Payment p) { payments.add(p); }
    public boolean removePayment(Payment p) { return payments.remove(p); }

    public double paid() {
        double s = 0;
        for (int ix17 = 0; ix17 < payments.size(); ix17++) {
            Payment p = payments.get(ix17);
            s += p.getAmount();
        }
        return s;
    }

    public double balance() { return Math.max(0, round(amount - paid())); }

    public String status() {
        if (balance() <= 0.004) return "Paid";
        if (due != null && due.isBefore(LocalDate.now())) return "Overdue";
        return paid() > 0 ? "Partial" : "Unpaid";
    }

    /**
     * Checks a payment before it is saved. Returns the problem as a message, or null when it is fine.
     * When a payment is being edited, pass it as "editing" so that its old amount is not counted twice.
     */
    public String checkPayment(double amount, LocalDate date, Payment editing) {
        if (amount <= 0) return "The amount must be greater than zero.";
        if (date.isAfter(LocalDate.now())) return "The payment date cannot be in the future.";
        double room = balance();
        if (editing != null) room += editing.getAmount();
        if (amount > room + 0.004) return "The amount is more than the balance of " + Ui.money(room) + ".";
        return null;
    }

    /** The balance that was still open right after the given payment was made. */
    public double balanceAfter(Payment payment) {
        double paidSoFar = 0;
        for (int i = 0; i < payments.size(); i++) {
            paidSoFar += payments.get(i).getAmount();
            if (payments.get(i) == payment) break;
        }
        return Math.max(0, round(amount - paidSoFar));
    }

    static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}

/** One payment made on an invoice. Its code is the official receipt number. */
class Payment extends Entity {
    private LocalDate date;
    private double amount;
    private String method;
    private String recordedBy;  // the staff member who entered it
    private String payer;       // who actually paid (usually the client)

    Payment(int id, LocalDate date, double amount, String method, String recordedBy, String payer) {
        super(id);
        this.date = date;
        setAmount(amount);
        setMethod(method);
        setRecordedBy(recordedBy);
        setPayer(payer);
    }

    @Override public String code() { return String.format("OR-%04d", getId()); }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = Math.max(0, amount); }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = clean(method); }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = clean(recordedBy); }

    public String getPayer() { return payer; }
    public void setPayer(String payer) { this.payer = clean(payer); }
}

/** A document record attached to a case. */
class Doc extends CaseRecord {
    public static final String[] TYPES = {"Pleading", "Contract", "Evidence", "Correspondence", "Court Order", "Identification", "Other"};
    public static final String[] STATUSES = {"Draft", "Filed", "Final", "Archived"};

    private String name;
    private String type;
    private LocalDate added;
    private String location;   // path of the stored file (or a note for records without a file)
    private String status;
    private String addedBy;

    Doc(int id, String name, String type, Case relatedCase, LocalDate added, String location, String status, String addedBy) {
        super(id, relatedCase);
        setName(name);
        setType(type);
        this.added = added;
        setLocation(location);
        setStatus(status);
        setAddedBy(addedBy);
    }

    @Override public String code() { return String.format("DOC-%04d", getId()); }

    public String getName() { return name; }
    public void setName(String name) { this.name = clean(name); }

    public String getType() { return type; }
    public void setType(String type) { this.type = clean(type); }

    public LocalDate getAdded() { return added; }
    public void setAdded(LocalDate added) { this.added = added; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = clean(location); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = clean(status); }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = clean(addedBy); }
}
