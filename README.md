# Del Rosario Law Offices — Case Management Portal (Java Swing)

The look of the original `LawFirmVol2` mock-ups (navy brand block, grey sidebar, flat buttons, gold
captions, add-record forms) with the working logic of the second project, extended.

## Run
    mvn package && java -jar target/LawFirmVol3-2.0.jar
or run `com.mycompany.lawfirmvol2.LawFirmVol2` from NetBeans (JDK 17+).

Demo accounts (created on first run): `admin / admin123`, `reyes / lawyer123`
(also `navarro`, `elizon` lawyers, same password). Clients are records kept by the staff; they do not sign in.

Data is saved automatically as plain text (`.txt`) files in the **`data/` folder of the project**, written and read with the `java.io`
classes: `users.txt`, `clients.txt`, `cases.txt`, `appointments.txt`, `invoices.txt`, `payments.txt`,
`documents.txt` and `activity.txt` (one line per record, fields separated by `|`, first line names the columns; a typed `|` is saved as `/`).
Documents are copied to `data/files/`. Start the program from the project folder (NetBeans and `mvn` do this). Use `-Dlawfirm.data=/path/to/folder` to keep the
data elsewhere. Delete the `.txt` files in `data/` to start over with the demo data. (A `lawfirm.db` file from an older version is no longer read.)

## What each role gets
| | Admin | Lawyer |
|---|---|---|
| Dashboard, cases, schedule, documents, billing | all firm data | own cases only |
| Add / edit cases | yes (+ remove, reassign) | yes (own) |
| Client contacts (add / edit / delete) | yes | yes |
| Appointments | add/edit/cancel/delete | add/edit/cancel/delete |
| Invoices & payments | yes | yes |
| Documents | add/open/remove | add/open/remove |
| Users, assignments, activity log | yes | no |

## Changes compared with the second project
Design: file-1 palette/fonts, Admin/Law Firm portal labels, breadcrumb bar, full-page forms
(Add Case, Add Appointment, Add User, Add Document, Add Invoice), navy "next event" banner, stat tiles,
"Pending tasks" panel, paged tables (10/page), Previous/Next pager.

Functionality: saved data; salted PBKDF2 passwords; a three-tries block that an administrator can lift;
show-password; change own password; documents module; invoices + partial payments + overdue status +
printable summary; invoice edit/delete and payment edit/delete (deletes ask for an administrator password); payer name and numbered official receipts (OR-0001...) that can be printed or saved; Pending status; case editing; first hearing from the Add Case
form; double-booking detection; date/time pickers; sortable columns; search; text-file (.txt) export on every table;
reports grouped by type / lawyer / client / status; user edit, disable, reset password, delete (with
last-admin and self protection); activity log.

## New in this version
- **Three tries, then blocked.** Three wrong passwords in a row block that account. A blocked lawyer cannot sign in
  (even with the right password) until an administrator opens *User Accounts* and presses **Block / Unblock**. An
  administrator is only locked out for 30 seconds, so the firm can never lock itself out. The counter and the block
  are saved in `users.txt`, so restarting the program does not reset them. Admins can also block an account by hand.
- **Tooltips and keyboard shortcuts.** Every button, field, table and menu entry has a hover tip; press **F1** for the
  list. Ctrl+1...0 open the menu entries, Ctrl+N new, Ctrl+E edit, Delete remove, Enter open, Ctrl+F search,
  Alt+Left/Right page, Ctrl+S save, Esc cancel, F5 reload, Ctrl+L sign out.
- **Encapsulation and inheritance.** Every field of every record is private, with getters/setters that trim text and
  refuse negative amounts. `Data` keeps its lists private and gives out read-only views. The records share base
  classes: `Entity` (id + code) > `Person` (name, email, phone) > `Client`, `User`; and `Entity` > `CaseRecord` (belongs
  to a case) > `Appointment`, `Invoice`, `Doc`; plus `Case` and `Payment`. The pages already inherit `Page` > `TablePage`.
- **OOP principles are highlighted.** Every key example in the code has a comment starting with `// >>> OOP |`
  followed by the principle (ENCAPSULATION, INHERITANCE, ABSTRACTION, POLYMORPHISM, COMPOSITION): search for
  `>>> OOP` in your editor. `docs/OOP-highlights.html` (open it in a browser) collects all of them, colour-coded,
  with the code and the file and line of each.
- **Plain text files (`java.io`)** for the data, the table export (`.txt`), receipts / summaries and document copies.
- **UML class diagrams** in `docs/` (`UML-1-model` and `UML-2-data-and-ui`, as `.png`, `.svg` and Graphviz `.dot`).

## Code layout (16 files, `src/main/java/com/mycompany/lawfirmvol2/`)
Related classes share a file. Only the class named after the file is `public`; the others in it are package-private
and are used by their simple names.

| File | Classes inside | What it is |
|---|---|---|
| `LawFirmVol2` | `LawFirmVol2` | `main` |
| `Shell` | `Shell` | the window: sidebar, top bar, page switching, keyboard shortcuts, F1 help |
| `LoginPanel` | `LoginPanel` | the sign-in screen |
| `Data` | `Data` | the lists, `.txt` saving and loading, the login / three-tries rule, demo data |
| `Models` | `Entity`, `Person`, `Client`, `User`, `Case`, `CaseRecord`, `Appointment`, `Invoice`, `Payment`, `Doc` | the records |
| `Ui` | `Ui`, `Btn` | colours, fonts, formatting, tooltips, small building blocks, the flat button |
| `Page` | `Page`, `Form`, `Saver`, `RefreshListener` | a screen, a full-page form, what it does on Save, the filter-box reload |
| `TablePage` | `TablePage`, `CellRenderer`, `HeaderRenderer` | the table screen (search, sort, paging, `.txt` export) and how its cells are drawn |
| `BillingPage` | `BillingPage`, `PaymentsDialog`, `Receipt` | invoices, the payments of one invoice, the official receipt |
| `AdminPages` | `UsersPage`, `AssignmentsPage`, `ActivityPage` | the admin-only menu entries |
| `DashboardPage`, `CasesPage`, `ClientsPage`, `SchedulePage`, `DocumentsPage`, `ReportsPage` | one each | the other menu entries |
