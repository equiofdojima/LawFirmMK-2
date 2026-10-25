import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * The in-memory data of the firm. Everything is saved as plain text (.txt) files, one file for each kind
 * of record, written and read with the java.io classes. The lists are private: other classes read them
 * through the getters (read-only views) and change them through the methods of this class.
 */
class Data {
    // >>> OOP | ENCAPSULATION: All the lists are private. Other classes must go through the getters and the methods of Data.
    private static final ArrayList<User> users = new ArrayList<>();
    private static final ArrayList<Client> clients = new ArrayList<>();
    private static final ArrayList<Case> cases = new ArrayList<>();
    private static final ArrayList<Appointment> appointments = new ArrayList<>();
    private static final ArrayList<Invoice> invoices = new ArrayList<>();
    private static final ArrayList<Doc> documents = new ArrayList<>();
    private static final ArrayList<String[]> log = new ArrayList<>(); // {timestamp, user, text}

    private static User currentUser;
    private static boolean seeded;     // true when this run created the demo data

    private static String loginError = "";   // why the last sign-in failed

    // ------------------------------------------------------------------ read-only views
    // >>> OOP | ENCAPSULATION: Read-only views: callers can look at the list but cannot add to it or remove from it.
    static List<User> getUsers() { return Collections.unmodifiableList(users); }
    static List<Client> getClients() { return Collections.unmodifiableList(clients); }
    static List<Case> getCases() { return Collections.unmodifiableList(cases); }
    static List<Appointment> getAppointments() { return Collections.unmodifiableList(appointments); }
    static List<Doc> getDocuments() { return Collections.unmodifiableList(documents); }
    static List<String[]> getLog() { return Collections.unmodifiableList(log); }

    static User getCurrentUser() { return currentUser; }
    static void setCurrentUser(User u) { currentUser = u; }
    static boolean isSeeded() { return seeded; }

    // ------------------------------------------------------------------ removing records
    static void removeUser(User u) { users.remove(u); }
    static void removeClient(Client k) { clients.remove(k); }
    static void removeAppointment(Appointment a) { appointments.remove(a); }
    static void removeInvoice(Invoice i) { invoices.remove(i); }
    static void removeDocument(Doc d) { documents.remove(d); }

    /** Removes a case together with everything that belongs to it. */
    // >>> OOP | ENCAPSULATION: The rule "removing a case also removes its appointments, invoices and documents" is kept in ONE place.
    static void removeCase(Case c) {
        cases.remove(c);
        for (int i = appointments.size() - 1; i >= 0; i--) if (appointments.get(i).getRelatedCase() == c) appointments.remove(i);
        for (int i = invoices.size() - 1; i >= 0; i--) if (invoices.get(i).getRelatedCase() == c) invoices.remove(i);
        for (int i = documents.size() - 1; i >= 0; i--) if (documents.get(i).getRelatedCase() == c) documents.remove(i);
    }

    private static int nUser = 1, nClient = 1, nCase = 1, nAppt = 1, nInv = 1, nPay = 1, nDoc = 1;

    // ------------------------------------------------------------------ location
    /**
     * The folder with the .txt files: "data" inside the project (the folder the program is started from).
     * Use -Dlawfirm.data=/some/folder to keep them somewhere else.
     */
    static File dataDir() {
        String custom = System.getProperty("lawfirm.data");
        if (custom != null && !custom.isBlank()) return new File(custom);
        return new File("data");
    }

    static File dataFile(String name) { return new File(dataDir(), name); }

    static File filesDir() { return new File(dataDir().getAbsoluteFile(), "files"); }

    // ------------------------------------------------------------------ start up
    static void init() {
        clearAll();
        if (!load()) {
            seed();
            seeded = true;
            save();
        }
    }

    static void clearAll() {
        users.clear(); clients.clear(); cases.clear(); appointments.clear(); invoices.clear(); documents.clear(); log.clear();
        nUser = nClient = nCase = nAppt = nInv = nPay = nDoc = 1;
        currentUser = null;
        seeded = false;
    }

    // ------------------------------------------------------------------ factories
    static User newUser(String username, String password, String fullName, String email, String phone, String role) {
        User u = new User(nUser++, username, fullName, email, phone, role);
        u.setPassword(password);
        users.add(u);
        return u;
    }

    static Client newClient(String name, String email, String phone) {
        Client c = new Client(nClient++, name, email, phone);
        clients.add(c);
        return c;
    }

    static Case newCase(String title, Client client, User lawyer, String type, double fee, String status) {
        Case c = new Case(nCase++, title, client, lawyer, type, fee, status);
        cases.add(c);
        return c;
    }

    static Appointment newAppointment(String event, Case c, LocalDate d, LocalTime t, String place, String status) {
        Appointment a = new Appointment(nAppt++, event, c, d, t, place, status);
        appointments.add(a);
        return a;
    }

    static Invoice newInvoice(Case c, String description, LocalDate issued, LocalDate due, double amount) {
        Invoice i = new Invoice(nInv++, c, description, issued, due, amount);
        invoices.add(i);
        return i;
    }

    static Payment newPayment(Invoice inv, LocalDate date, double amount, String method, String recordedBy, String payer) {
        Payment p = new Payment(nPay++, date, amount, method, recordedBy, payer);
        inv.addPayment(p);
        return p;
    }

    static Doc newDoc(String name, String type, Case c, String location, String status, String addedBy) {
        Doc d = new Doc(nDoc++, name, type, c, LocalDate.now(), location, status, addedBy);
        documents.add(d);
        return d;
    }

    // ------------------------------------------------------------------ demo data
    static void seed() {
        clearAll();
        newUser("admin", "admin123", "Firm Administrator", "admin@delrosario.example", "+63 2 8555 0100", User.ADMIN);
        User reyes = newUser("reyes", "lawyer123", "Atty. Maria Reyes", "mreyes@delrosario.example", "+63 917 555 0101", User.LAWYER);
        User navarro = newUser("navarro", "lawyer123", "Atty. Luis Navarro", "lnavarro@delrosario.example", "+63 917 555 0102", User.LAWYER);
        User elizon = newUser("elizon", "lawyer123", "Atty. Carla Elizon", "celizon@delrosario.example", "+63 917 555 0103", User.LAWYER);
        Client santos = newClient("Elena Santos", "elena.santos@mail.example", "+63 918 555 0201");
        Client cruz = newClient("Juan Cruz", "juan.cruz@mail.example", "+63 918 555 0202");
        Client garcia = newClient("Pedro Garcia", "pedro.garcia@mail.example", "+63 918 555 0203");

        LocalDate today = LocalDate.now();
        Case c1 = newCase("Santos Estate Settlement", santos, reyes, "Probate", 85000, Case.ACTIVE);
        c1.setCourt("RTC Makati, Br. 61"); c1.setOpposing("Heirs of R. Santos"); c1.setFiled(today.minusMonths(3));
        c1.setDescription("Settlement of the estate of the late Rodrigo Santos.");
        Case c2 = newCase("Cruz v. Pacific Holdings", cruz, navarro, "Civil", 120000, Case.ACTIVE);
        c2.setCourt("RTC Manila, Br. 32"); c2.setOpposing("Pacific Holdings Inc."); c2.setFiled(today.minusMonths(5));
        c2.setDescription("Collection of sum of money and damages.");
        Case c3 = newCase("Deed of Sale Review", cruz, reyes, "Property", 15000, Case.PENDING);
        c3.setDescription("Review of the deed of absolute sale for the Quezon City lot.");
        Case c4 = newCase("Garcia Labor Dispute", garcia, elizon, "Labor", 60000, Case.ACTIVE);
        c4.setCourt("NLRC NCR"); c4.setOpposing("Metro Logistics Corp."); c4.setFiled(today.minusMonths(1));
        c4.setDescription("Illegal dismissal and unpaid benefits.");
        Case c5 = newCase("Santos Family Trust", santos, navarro, "Family", 45000, Case.HANDLED);
        c5.setDescription("Establishment of a family trust (concluded).");

        newAppointment("Probate Hearing", c1, today.plusDays(1), LocalTime.of(9, 0), "RTC Makati, Br. 61", Appointment.CONFIRMED);
        newAppointment("Pre-trial Conference", c2, today.plusDays(3), LocalTime.of(13, 30), "RTC Manila, Br. 32", Appointment.CONFIRMED);
        newAppointment("Client Consultation", c3, today.plusDays(5), LocalTime.of(10, 0), "Conference Room 2", Appointment.TENTATIVE);
        newAppointment("Mandatory Conference", c4, today.plusDays(6), LocalTime.of(14, 0), "NLRC NCR", Appointment.CONFIRMED);
        newAppointment("Evidence Submission", c2, today.plusDays(9), LocalTime.of(11, 0), "RTC Manila, Br. 32", Appointment.CONFIRMED);
        newAppointment("Mediation Session", c1, today.plusDays(12), LocalTime.of(15, 0), "Conference Room 1", Appointment.TENTATIVE);
        newAppointment("Initial Consultation", c4, today.minusDays(4), LocalTime.of(10, 0), "Conference Room 2", Appointment.CONFIRMED);

        Invoice i1 = newInvoice(c1, "Legal fees", today.minusDays(60), today.minusDays(30), 85000);
        newPayment(i1, today.minusDays(55), 40000, "Bank transfer", "Firm Administrator", santos.getName());
        Invoice i2 = newInvoice(c2, "Legal fees", today.minusDays(90), today.plusDays(15), 120000);
        newPayment(i2, today.minusDays(80), 60000, "Check", "Firm Administrator", cruz.getName());
        newInvoice(c3, "Document review fee", today.minusDays(2), today.plusDays(28), 15000);
        Invoice i4 = newInvoice(c4, "Legal fees", today.minusDays(20), today.plusDays(10), 60000);
        newPayment(i4, today.minusDays(18), 20000, "Cash", "Firm Administrator", garcia.getName());
        Invoice i5 = newInvoice(c5, "Legal fees", today.minusDays(120), today.minusDays(90), 45000);
        newPayment(i5, today.minusDays(100), 45000, "Bank transfer", "Firm Administrator", santos.getName());

        Doc d1 = newDoc("Last Will and Testament", "Evidence", c1, "Firm records / Probate / will.pdf", "Filed", "Atty. Maria Reyes");
        d1.setAdded(today.minusDays(40));
        Doc d2 = newDoc("Complaint", "Pleading", c2, "Firm records / Civil / complaint.pdf", "Filed", "Atty. Luis Navarro");
        d2.setAdded(today.minusDays(70));
        Doc d3 = newDoc("Draft Deed of Sale", "Contract", c3, "Firm records / Property / deed-draft.docx", "Draft", "Atty. Maria Reyes");
        d3.setAdded(today.minusDays(2));
        Doc d4 = newDoc("Position Paper", "Pleading", c4, "Firm records / Labor / position-paper.pdf", "Draft", "Atty. Carla Elizon");
        d4.setAdded(today.minusDays(6));
        log.add(new String[]{LocalDateTime.now().withNano(0).toString(), "System", "Demo data created"});
    }

    // ------------------------------------------------------------------ queries
    static User findUser(String username) {
        if (username == null) return null;
        for (int ix1 = 0; ix1 < users.size(); ix1++) {
            User u = users.get(ix1);
            if (u.getUsername().equalsIgnoreCase(username.trim())) return u;
        }
        return null;
    }

    /** Why the last call of login() returned null (shown on the sign-in screen). */
    static String getLoginError() { return loginError; }

    /**
     * One sign-in attempt: returns the user, or null (getLoginError() then tells why).
     * Three wrong passwords in a row block a lawyer's account until an administrator unblocks it.
     * An administrator is only locked for 30 seconds, so the firm can never lock itself out.
     */
    static User login(String username, String password) {
        User u = findUser(username);
        if (u == null) {
            loginError = "Invalid username or password.";
            return null;
        }
        if (u.isBlocked()) {
            loginError = "This account is blocked. Ask an administrator to unblock it.";
            return null;
        }
        if (u.lockSecondsLeft() > 0) {
            loginError = "Too many wrong tries. Try again in " + u.lockSecondsLeft() + " seconds.";
            return null;
        }
        if (!u.checkPassword(password)) {
            boolean thirdTry = u.recordFailure();
            if (!thirdTry) {
                loginError = "Invalid username or password. Tries left: " + u.attemptsLeft();
            } else if (u.isBlocked()) {
                systemLog("Account " + u.getUsername() + " was blocked after " + User.MAX_ATTEMPTS + " wrong passwords");
                loginError = "Too many wrong tries. This account is now blocked. Ask an administrator to unblock it.";
            } else {
                systemLog("Administrator " + u.getUsername() + " was locked for " + User.ADMIN_LOCK_SECONDS + " seconds");
                loginError = "Too many wrong tries. Try again in " + User.ADMIN_LOCK_SECONDS + " seconds.";
            }
            save();   // the count is kept even if the program is closed
            return null;
        }
        u.recordSuccess();
        return u;
    }

    /** Adds a line to the activity log without needing anyone to be signed in. */
    private static void systemLog(String what) {
        log.add(new String[]{LocalDateTime.now().withNano(0).toString(), "System", what});
    }

    static ArrayList<User> usersByRole(String role) {
        ArrayList<User> list = new ArrayList<>();
        for (int ix2 = 0; ix2 < users.size(); ix2++) {
            User u = users.get(ix2);
            if (u.getRole().equals(role) && u.isActive()) list.add(u);
        }
        return list;
    }

    static ArrayList<Case> visibleCases() {
        ArrayList<Case> list = new ArrayList<>();
        for (int ix3 = 0; ix3 < cases.size(); ix3++) {
            Case c = cases.get(ix3);
            if (c.isVisibleTo(currentUser)) list.add(c);
        }
        return list;
    }

    static ArrayList<Appointment> visibleAppointments() {
        ArrayList<Appointment> list = new ArrayList<>();
        for (int ix4 = 0; ix4 < appointments.size(); ix4++) {
            Appointment a = appointments.get(ix4);
            if (a.getRelatedCase().isVisibleTo(currentUser)) list.add(a);
        }
        // insertion sort: earliest appointment first
        for (int i = 1; i < list.size(); i++) {
            Appointment current = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).when().isAfter(current.when())) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, current);
        }
        return list;
    }

    static ArrayList<Invoice> visibleInvoices() {
        ArrayList<Invoice> list = new ArrayList<>();
        for (int ix5 = 0; ix5 < invoices.size(); ix5++) {
            Invoice i = invoices.get(ix5);
            if (i.getRelatedCase().isVisibleTo(currentUser)) list.add(i);
        }
        return list;
    }

    static ArrayList<Doc> visibleDocuments() {
        ArrayList<Doc> list = new ArrayList<>();
        for (int ix6 = 0; ix6 < documents.size(); ix6++) {
            Doc d = documents.get(ix6);
            if (d.getRelatedCase().isVisibleTo(currentUser)) list.add(d);
        }
        return list;
    }

    static Appointment nextHearing(Case c) {
        Appointment best = null;
        for (int ix7 = 0; ix7 < appointments.size(); ix7++) {
            Appointment a = appointments.get(ix7);
            if (a.getRelatedCase() != c || !a.isUpcoming()) continue;
            if (best == null || a.when().isBefore(best.when())) best = a;
        }
        return best;
    }

    static ArrayList<Invoice> invoicesOf(Case c) {
        ArrayList<Invoice> list = new ArrayList<>();
        for (int ix8 = 0; ix8 < invoices.size(); ix8++) {
            Invoice i = invoices.get(ix8);
            if (i.getRelatedCase() == c) list.add(i);
        }
        return list;
    }

    /** Another live appointment of the same lawyer at the same date and time (or null). */
    static Appointment findConflict(User lawyer, LocalDate d, LocalTime t, Appointment ignore) {
        for (int ix9 = 0; ix9 < appointments.size(); ix9++) {
            Appointment a = appointments.get(ix9);
            if (a == ignore || a.getStatus().equals(Appointment.CANCELLED)) continue;
            if (a.getLawyer() == lawyer && a.getDate().equals(d) && a.getTime().equals(t)) return a;
        }
        return null;
    }

    /** True when the password belongs to any active administrator (used before risky deletes). */
    static boolean verifyAdminPassword(String password) {
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (u.getRole().equals(User.ADMIN) && u.isActive() && u.checkPassword(password)) return true;
        }
        return false;
    }

    static int countAdmins() {
        int n = 0;
        for (int ix10 = 0; ix10 < users.size(); ix10++) {
            User u = users.get(ix10);
            if (u.getRole().equals(User.ADMIN) && u.isActive()) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ audit + save
    /** Records what happened (for the Activity Log) and saves everything to disk. */
    static void commit(String what) {
        if (what != null) {
            String who = currentUser == null ? "System" : currentUser.getName();
            log.add(new String[]{LocalDateTime.now().withNano(0).toString(), who, what});
            while (log.size() > 1000) log.remove(0);
        }
        save();
    }

    // ------------------------------------------------------------------ text files (java.io)
    // One .txt file for each kind of record, one line for each record, fields separated by "|".
    // The first line of every file starts with "#" and names the columns, so a person can read the files.
    // A "|" typed by the user is saved as "/", and a line break is saved as the two characters \n.

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("|", "/").replace("\r", "").replace("\n", "\\n");
    }

    /** Builds one line of a file from its fields. */
    private static String line(Object... fields) {
        String text = "";
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) text = text + "|";
            if (fields[i] != null) text = text + safe(fields[i].toString());
        }
        return text;
    }

    /** Splits one line of a file into its fields. */
    private static String[] split(String text) {
        String[] p = text.split("\\|", -1);
        for (int i = 0; i < p.length; i++) p[i] = p[i].replace("\\n", "\n");
        return p;
    }

    private static LocalDate toDate(String s) { return s.isEmpty() || s.equals("null") ? null : LocalDate.parse(s); }

    private static void writeFile(String name, String columns, ArrayList<String> lines) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(dataFile(name), StandardCharsets.UTF_8))) {
            out.println("# " + columns);
            for (int i = 0; i < lines.size(); i++) out.println(lines.get(i));
        }
    }

    /** The data lines of one file (the "#" line and empty lines are skipped). A missing file gives no lines. */
    private static ArrayList<String[]> readFile(String name) throws IOException {
        ArrayList<String[]> rows = new ArrayList<>();
        File f = dataFile(name);
        if (!f.exists()) return rows;
        try (BufferedReader in = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String text;
            while ((text = in.readLine()) != null) {
                if (text.isEmpty() || text.startsWith("#")) continue;
                rows.add(split(text));
            }
        }
        return rows;
    }

    /** Copies a file byte by byte. */
    static void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }

    // ------------------------------------------------------------------ saving
    static boolean save() {
        try {
            dataDir().getAbsoluteFile().mkdirs();
            saveUsers();
            saveClients();
            saveCases();
            saveAppointments();
            saveInvoicesAndPayments();
            saveDocuments();
            saveLog();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void saveUsers() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            lines.add(line(u.getId(), u.getUsername(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                    u.getPasswordHash(), u.isActive(), u.isBlocked(), u.getFailedAttempts()));
        }
        writeFile("users.txt", "id|username|full name|email|phone|role|password hash|active|blocked|failed tries", lines);
    }

    private static void saveClients() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            Client k = clients.get(i);
            lines.add(line(k.getId(), k.getName(), k.getEmail(), k.getPhone()));
        }
        writeFile("clients.txt", "id|name|email|phone", lines);
    }

    private static void saveCases() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            lines.add(line(c.getId(), c.getTitle(), c.getClient().getId(), c.getLawyer().getId(), c.getType(), c.getFee(),
                    c.getStatus(), c.getOpposing(), c.getCourt(), c.getFiled(), c.getOpened(), c.getDescription()));
        }
        writeFile("cases.txt", "id|title|client id|lawyer id|type|fee|status|opposing party|court|date filed|date opened|description", lines);
    }

    private static void saveAppointments() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < appointments.size(); i++) {
            Appointment a = appointments.get(i);
            lines.add(line(a.getId(), a.getEvent(), a.getRelatedCase().getId(), a.getDate(), a.getTime(), a.getLocation(), a.getStatus()));
        }
        writeFile("appointments.txt", "id|event|case id|date|time|location|status", lines);
    }

    private static void saveInvoicesAndPayments() throws IOException {
        ArrayList<String> invoiceLines = new ArrayList<>();
        ArrayList<String> paymentLines = new ArrayList<>();
        for (int i = 0; i < invoices.size(); i++) {
            Invoice inv = invoices.get(i);
            invoiceLines.add(line(inv.getId(), inv.getRelatedCase().getId(), inv.getDescription(), inv.getIssued(), inv.getDue(), inv.getAmount()));
            for (int k = 0; k < inv.getPayments().size(); k++) {
                Payment p = inv.getPayments().get(k);
                paymentLines.add(line(p.getId(), inv.getId(), p.getDate(), p.getAmount(), p.getMethod(), p.getRecordedBy(), p.getPayer()));
            }
        }
        writeFile("invoices.txt", "id|case id|description|issued|due|amount", invoiceLines);
        writeFile("payments.txt", "receipt id|invoice id|date|amount|method|recorded by|payer", paymentLines);
    }

    private static void saveDocuments() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Doc d = documents.get(i);
            lines.add(line(d.getId(), d.getName(), d.getType(), d.getRelatedCase().getId(), d.getAdded(),
                    d.getLocation(), d.getStatus(), d.getAddedBy()));
        }
        writeFile("documents.txt", "id|name|type|case id|date added|location|status|added by", lines);
    }

    private static void saveLog() throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < log.size(); i++) {
            String[] l = log.get(i);
            lines.add(line(l[0], l[1], l[2]));
        }
        writeFile("activity.txt", "timestamp|user|action", lines);
    }

    // ------------------------------------------------------------------ loading
    // while loading, a record points to another one by its id (a case has a client id and a lawyer id)
    private static final HashMap<Integer, User> userById = new HashMap<>();
    private static final HashMap<Integer, Client> clientById = new HashMap<>();
    private static final HashMap<Integer, Case> caseById = new HashMap<>();
    private static final HashMap<Integer, Invoice> invoiceById = new HashMap<>();

    /** Reads the files. False when there is no data yet (then the demo data is created). */
    static boolean load() {
        if (!dataFile("users.txt").exists()) return false;
        userById.clear();
        clientById.clear();
        caseById.clear();
        invoiceById.clear();
        try {
            loadUsers();
            loadClients();
            loadCases();
            loadAppointments();
            loadInvoices();
            loadPayments();
            loadDocuments();
            loadLog();
            return !users.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    // In each method a damaged line is skipped (catch) instead of stopping the whole program.
    private static void loadUsers() throws IOException {
        ArrayList<String[]> rows = readFile("users.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                User u = new User(Integer.parseInt(p[0]), p[1], p[2], p[3], p[4], p[5]);
                String password = p[6];
                int next = 7;
                if (!password.contains(":")) {
                    password = p[6] + ":" + p[7];
                    next = 8;
                }
                u.setPasswordHash(password);
                if (p.length > next && p[next].equals("false")) u.block();   // older files: a disabled account stays blocked
                if (p.length > next + 1 && p[next + 1].equals("true")) u.block();
                if (p.length > next + 2) u.setFailedAttempts(Integer.parseInt(p[next + 2]));
                users.add(u);
                userById.put(u.getId(), u);
                nUser = Math.max(nUser, u.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadClients() throws IOException {
        ArrayList<String[]> rows = readFile("clients.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Client k = new Client(Integer.parseInt(p[0]), p[1], p[2], p[3]);
                clients.add(k);
                clientById.put(k.getId(), k);
                nClient = Math.max(nClient, k.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadCases() throws IOException {
        ArrayList<String[]> rows = readFile("cases.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Client client = clientById.get(Integer.parseInt(p[2]));
                User lawyer = userById.get(Integer.parseInt(p[3]));
                if (client == null || lawyer == null) continue;
                Case c = new Case(Integer.parseInt(p[0]), p[1], client, lawyer, p[4], Double.parseDouble(p[5]), p[6]);
                c.setOpposing(p[7]);
                c.setCourt(p[8]);
                c.setFiled(toDate(p[9]));
                c.setOpened(p[10].isEmpty() ? LocalDate.now() : LocalDate.parse(p[10]));
                c.setDescription(p[11]);
                cases.add(c);
                caseById.put(c.getId(), c);
                nCase = Math.max(nCase, c.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadAppointments() throws IOException {
        ArrayList<String[]> rows = readFile("appointments.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Case c = caseById.get(Integer.parseInt(p[2]));
                if (c == null) continue;
                Appointment a = new Appointment(Integer.parseInt(p[0]), p[1], c, LocalDate.parse(p[3]), LocalTime.parse(p[4]), p[5], p[6]);
                appointments.add(a);
                nAppt = Math.max(nAppt, a.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadInvoices() throws IOException {
        ArrayList<String[]> rows = readFile("invoices.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Case c = caseById.get(Integer.parseInt(p[1]));
                if (c == null) continue;
                Invoice inv = new Invoice(Integer.parseInt(p[0]), c, p[2], toDate(p[3]), toDate(p[4]), Double.parseDouble(p[5]));
                invoices.add(inv);
                invoiceById.put(inv.getId(), inv);
                nInv = Math.max(nInv, inv.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadPayments() throws IOException {
        ArrayList<String[]> rows = readFile("payments.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Invoice inv = invoiceById.get(Integer.parseInt(p[1]));
                if (inv == null) continue;
                Payment pay = new Payment(Integer.parseInt(p[0]), toDate(p[2]), Double.parseDouble(p[3]), p[4], p[5], p[6]);
                inv.addPayment(pay);
                nPay = Math.max(nPay, pay.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadDocuments() throws IOException {
        ArrayList<String[]> rows = readFile("documents.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            try {
                Case c = caseById.get(Integer.parseInt(p[3]));
                if (c == null) continue;
                Doc d = new Doc(Integer.parseInt(p[0]), p[1], p[2], c, toDate(p[4]), p[5], p[6], p[7]);
                documents.add(d);
                nDoc = Math.max(nDoc, d.getId() + 1);
            } catch (RuntimeException e) {
                // skip
            }
        }
    }

    private static void loadLog() throws IOException {
        ArrayList<String[]> rows = readFile("activity.txt");
        for (int i = 0; i < rows.size(); i++) {
            String[] p = rows.get(i);
            if (p.length >= 3) log.add(new String[]{p[0], p[1], p[2]});
        }
    }
}
