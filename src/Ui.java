import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.*;

/** Small flat widgets that give every screen the same look. */
public final class Ui {

    public static Font baseRegular;
    public static Font baseBold;
    public static Font TITLE;
    public static Font HEAD_F;
    public static Font SUB;
    public static Font BOLD;
    public static Font PLAIN;
    public static Font SMALL;
    public static Font BIG;
    public static Font TAG;
    public static Font LOGO;
    public static Font MONO;


    // ---- colours ----
    public static final Color NAVY    = new Color(15, 40, 70);
    public static final Color BLUE    = new Color(36, 84, 116);
    public static final Color BLUE_HI = new Color(48, 104, 142);
    public static final Color GOLD    = new Color(150, 110, 40);
    public static final Color PAGE    = new Color(216, 220, 225);
    public static final Color SIDE    = new Color(235, 236, 238);
    public static final Color LIGHT   = new Color(245, 245, 245);
    public static final Color HEAD    = new Color(232, 234, 237);
    public static final Color ROW     = new Color(240, 243, 242);
    public static final Color LINE    = new Color(205, 208, 214);
    public static final Color GRAY    = new Color(110, 115, 125);
    public static final Color SOFT    = new Color(190, 200, 215);
    public static final Color AVATAR  = new Color(240, 228, 208);
    public static final Color TOPBAR  = new Color(243, 243, 243);
    public static final Color TEXT    = new Color(40, 45, 55);
    public static final Color RED     = new Color(179, 38, 30);
    public static final Color GREEN   = new Color(47, 107, 79);

    static{
        loadFonts();
    }

    // ---- fonts ----

    public static void loadFonts(){

        // default font custom fonts dont work
        TITLE  = new Font("Arial", Font.BOLD, 24);
        HEAD_F = new Font("Arial", Font.BOLD, 20);
        SUB    = new Font("Arial", Font.BOLD, 15);
        BOLD   = new Font("Arial", Font.BOLD, 12);
        PLAIN  = new Font("Arial", Font.PLAIN, 12);
        SMALL  = new Font("Arial", Font.PLAIN, 11);
        BIG    = new Font("Arial", Font.BOLD, 28);
        TAG    = new Font("Arial", Font.BOLD, 10);
        LOGO   = new Font("Serif", Font.BOLD, 22);
        MONO   = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        try {
            // load the main font from the fonts folder
            Font base = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("Fonts/AndersonGrotesk-Bold.ttf"));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);

            // load the bold version
            Font boldBase = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("Fonts/AndersonGrotesk-UltraBold.ttf"));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);

            // load the font for the receipts / log areas
            Font mono = Font.createFont(Font.TRUETYPE_FONT, new java.io.File("Fonts/AndersonGrotesk-Light.ttf"));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);

            baseRegular = base;
            baseBold = boldBase;
            TITLE  = boldBase.deriveFont(Font.BOLD, 24f);
            HEAD_F = boldBase.deriveFont(Font.BOLD, 20f);
            SUB    = boldBase.deriveFont(Font.BOLD, 15f);
            BOLD   = boldBase.deriveFont(Font.BOLD, 12f);
            PLAIN  = base.deriveFont(Font.PLAIN, 12f);
            SMALL  = base.deriveFont(Font.PLAIN, 11f);
            BIG    = boldBase.deriveFont(Font.BOLD, 28f);
            TAG    = boldBase.deriveFont(Font.BOLD, 10f);
            LOGO   = boldBase.deriveFont(Font.BOLD, 22f);
            MONO   = mono.deriveFont(Font.PLAIN, 12f);

        } catch (Exception e) {
            System.out.println("Could not load custom fonts, using defaults: " + e.getMessage());
        }

    }

    // ---- keyboard shortcuts and tooltips ----
    public static final String MOD = "Ctrl+";   // shown in the tooltips
    public static final int MASK = java.awt.event.InputEvent.CTRL_DOWN_MASK;

    /** The hover text of a button (null = none). Every "+ Add ..." button creates a record. */
    public static String tipFor(String buttonText) {
        switch (buttonText) {
            case "Edit":                 return "Edit the selected row (" + MOD + "E)";
            case "Delete":               return "Delete the selected row (Delete key)";
            case "Remove":               return "Remove the selected row (Delete key)";
            case "Export":               return "Save the rows shown in the table as a .txt file";
            case "Cancel":               return "Go back without saving (Esc)";
            case "Close":                return "Close this panel";
            case "Change Status":        return "Change the status of the selected row";
            case "Reset Password":       return "Give the selected account a new password";
            case "Block / Unblock":      return "Block the selected account so it cannot sign in, or unblock it (also after 3 wrong passwords)";
            case "Reassign Case":        return "Move the selected case to another lawyer";
            case "Open File":            return "Open the stored file with your computer's default program";
            case "Browse...":            return "Choose a file from your computer";
            case "Record Payment":       return "Record a payment on the selected invoice";
            case "Receipt":              return "Show the official receipt of the selected payment";
            case "Print Summary":        return "Show a printable summary of all invoices";
            case "Payments & Receipts":  return "See, edit or delete the payments of the selected invoice";
            case "Cancel Appointment":   return "Mark the selected appointment as cancelled";
            case "Previous":             return "Previous page (Alt+Left)";
            case "Next":                 return "Next page (Alt+Right)";
            case "Sign in to LawFirm Portal": return "Sign in with the username and password above (Enter)";
            default:
                if (buttonText.startsWith("+")) return "Create a new record (" + MOD + "N)";
                return null;
        }
    }

    /** Gives a component its hover text. */
    public static void tip(JComponent c, String text) {
        c.setToolTipText(text);
    }

    // ---- peso amounts and dates ----
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public static String money(double v) { return "\u20B1" + String.format(Locale.ENGLISH, "%,.2f", v); }

    /** Marks a number as a peso amount inside a table row (shown with the sign, right-aligned, sorted numerically). */
    public static Double peso(double v) { return v; }

    public static String date(LocalDate d) { return d == null ? "" : d.format(DATE_FMT); }

    public static String time(LocalTime t) { return t == null ? "" : t.format(TIME_FMT); }

    // ---------------------------------------------------------------- text
    public static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    /** Small gold caps label above a page title ("CASE RECORDS"). */
    public static JLabel eyebrow(String text) {
        return label(text, TAG, GOLD);
    }

    public static JPanel heading(String eyebrow, String title, JComponent east) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel e = eyebrow(eyebrow);
        e.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = label(title, TITLE, TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.setBorder(new EmptyBorder(3, 0, 0, 0));
        left.add(e);
        left.add(t);
        p.add(left, BorderLayout.WEST);
        if (east != null) p.add(east, BorderLayout.EAST);
        return p;
    }


    /** A flat button that runs the given listener when clicked. */
    public static Btn button(String text, boolean primary, ActionListener listener) {
        Btn b = new Btn(text, primary);
        b.addActionListener(listener);
        return b;
    }

    public static Btn primary(String text) { return new Btn(text, true); }

    public static Btn secondary(String text) { return new Btn(text, false); }

    /** A flat text-only link ("View schedule >"). */
    public static JLabel link(String text, final String pageKey) {
        JLabel l = label(text, TAG, BLUE);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { Shell.current.navigate(pageKey); }
        });
        return l;
    }

    // ---------------------------------------------------------------- inputs
    public static Border inputBorder() {
        return new CompoundBorder(new LineBorder(LINE, 1), new EmptyBorder(4, 6, 4, 6));
    }

    public static JTextField field(String text) {
        JTextField f = new JTextField(text);
        f.setFont(PLAIN);
        f.setBorder(inputBorder());
        f.setPreferredSize(new Dimension(160, 30));
        return f;
    }

    public static JPasswordField password() {
        JPasswordField f = new JPasswordField();
        f.setFont(PLAIN);
        f.setEchoChar('*');
        f.setBorder(inputBorder());
        f.setPreferredSize(new Dimension(160, 30));
        return f;
    }

    public static JTextArea area(int rows) {
        JTextArea a = new JTextArea(rows, 20);
        a.setFont(PLAIN);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(new EmptyBorder(4, 6, 4, 6));
        return a;
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(new LineBorder(LINE, 1));
        return s;
    }

    public static JComboBox<String> combo(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(PLAIN);
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(150, 30));
        return c;
    }

    public static JSpinner dateSpinner(LocalDate d) {
        JSpinner s = new JSpinner(new SpinnerDateModel(toDate(d.atStartOfDay()), null, null, java.util.Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, "yyyy-MM-dd"));
        s.setFont(PLAIN);
        s.setPreferredSize(new Dimension(150, 30));
        return s;
    }

    public static LocalDate dateOf(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static JSpinner timeSpinner(LocalTime t) {
        JSpinner s = new JSpinner(new SpinnerDateModel(toDate(LocalDate.now().atTime(t)), null, null, java.util.Calendar.MINUTE));
        s.setEditor(new JSpinner.DateEditor(s, "HH:mm"));
        s.setFont(PLAIN);
        s.setPreferredSize(new Dimension(150, 30));
        return s;
    }

    public static LocalTime timeOf(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
    }

    private static Date toDate(java.time.LocalDateTime t) {
        return Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ---------------------------------------------------------------- cards
    /** Stat tile: small caption + big number. */
    public static JPanel statCard(String caption, JLabel value, Color numberColor) {
        JPanel c = new JPanel(new BorderLayout(0, 4));
        c.setBackground(Color.WHITE);
        c.setBorder(new CompoundBorder(new LineBorder(LINE, 1), new EmptyBorder(10, 14, 10, 14)));
        c.add(label(caption, BOLD, NAVY), BorderLayout.NORTH);
        value.setFont(BIG);
        value.setForeground(numberColor);
        c.add(value, BorderLayout.CENTER);
        return c;
    }

    /** White panel with a title row and an optional link at the right, then a body. */
    public static JPanel section(String title, JComponent right, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new LineBorder(LINE, 1));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(10, 12, 8, 12));
        head.add(label(title, SUB, TEXT), BorderLayout.WEST);
        if (right != null) head.add(right, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    // ---------------------------------------------------------------- dialogs
    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Notice", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Shows a small form in a dialog; true when the user pressed OK. */
    public static boolean ask(Component parent, String title, JComponent form) {
        return JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /**
     * Asks for an administrator's password before a risky action (such as a delete).
     * Returns true only when the password is correct.
     */
    public static boolean askAdminPassword(Component parent, String action) {
        JPasswordField pass = password();
        JPanel form = dialogForm();
        row(form, "Action:", label(action, PLAIN, TEXT));
        row(form, "Administrator password:", pass);
        if (!ask(parent, "Administrator approval", form)) return false;
        if (!Data.verifyAdminPassword(new String(pass.getPassword()))) {
            warn(parent, "That is not an administrator password. Nothing was changed.");
            return false;
        }
        return true;
    }

    /** Shows a text report in a dialog with Print, Save as text and Close buttons. */
    public static void showPrintable(Component parent, String title, String text, String fileName, int width, int height) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(width, height));
        Object[] options = {"Print...", "Save as text...", "Close"};
        int choice = JOptionPane.showOptionDialog(parent, scroll, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[2]);
        if (choice == 0) {
            try {
                area.print();
            } catch (java.awt.print.PrinterException ex) {
                warn(parent, "Printing failed: " + ex.getMessage());
            }
        } else if (choice == 1) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File(fileName));
            if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
                try (java.io.PrintWriter out = new java.io.PrintWriter(
                        new java.io.FileWriter(chooser.getSelectedFile(), java.nio.charset.StandardCharsets.UTF_8))) {
                    out.print(area.getText());
                    Shell.status("Saved " + chooser.getSelectedFile().getName() + ".");
                } catch (java.io.IOException ex) {
                    warn(parent, "Could not write the file: " + ex.getMessage());
                }
            }
        }
    }

    public static JPanel dialogForm() {
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        return p;
    }

    public static void row(JPanel form, String label, JComponent input) {
        form.add(label(label, PLAIN, TEXT));
        form.add(input);
    }

    public static String[] names(java.util.List<User> list) {
        String[] s = new String[list.size()];
        for (int i = 0; i < s.length; i++) s[i] = list.get(i).getName();
        return s;
    }

    public static String[] clientNames(java.util.List<Client> list) {
        String[] s = new String[list.size()];
        for (int i = 0; i < s.length; i++) s[i] = list.get(i).getName();
        return s;
    }

    public static String[] caseLabels(java.util.List<Case> list) {
        String[] s = new String[list.size()];
        for (int i = 0; i < s.length; i++) s[i] = list.get(i).code() + "  " + list.get(i).getTitle();
        return s;
    }

    // ---------------------------------------------------------------- brand
    public static JPanel brand(int height) {
        JPanel b = new JPanel();
        b.setBackground(NAVY);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        b.setBorder(new EmptyBorder(height / 2 - 26, 20, 0, 10));
        JLabel a = label(Case.getFirmShort(), LOGO, Color.WHITE);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = label("LAW OFFICES", TAG, SOFT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.add(a);
        b.add(l);
        b.setPreferredSize(new Dimension(230, height));
        return b;
    }

    /** Beige square with the initials of a person. */
    public static JLabel avatar(String initials, int size) {
        JLabel l = new JLabel(initials, SwingConstants.CENTER);
        l.setOpaque(true);
        l.setBackground(AVATAR);
        l.setForeground(GOLD);
        l.setFont(BOLD.deriveFont(Font.BOLD, (float) Math.max(9, size / 3)));
        l.setPreferredSize(new Dimension(size, size));
        return l;
    }
}

// ---------------------------------------------------------------- buttons
// >>> OOP | INHERITANCE: Btn IS-A JButton with the portal's flat look added.
class Btn extends JButton {
    private final boolean primary;

    public Btn(String text, boolean primary) {
        super(text);
        this.primary = primary;
        setFont(Ui.BOLD);
        setForeground(primary ? Color.WHITE : Ui.NAVY);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(8, 18, 8, 18));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(Ui.tipFor(text));
    }

    public boolean isPrimary() { return primary; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        int w = getWidth(), h = getHeight();
        ButtonModel m = getModel();
        if (!isEnabled()) {
            g.setColor(primary ? Ui.SOFT : Ui.LIGHT);
        } else if (primary) {
            g.setColor(m.isPressed() ? Ui.NAVY : m.isRollover() ? Ui.BLUE_HI : Ui.BLUE);
        } else {
            g.setColor(m.isPressed() ? Ui.HEAD : m.isRollover() ? Ui.LIGHT : Color.WHITE);
        }
        g.fillRect(0, 0, w, h);
        if (!primary) {
            g.setColor(Ui.LINE);
            g.drawRect(0, 0, w - 1, h - 1);
        }
        g.dispose();
        super.paintComponent(g0);
    }
}
