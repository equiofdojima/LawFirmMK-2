import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** The whole window: the login screen, and after login the sidebar + top bar + pages. */
public class Shell extends JPanel {
    static Shell current;
    static final JLabel statusLabel = Ui.label(" ", Ui.SMALL, Ui.GRAY);

    static void status(String message) {
        statusLabel.setText(message == null ? " " : message);
    }

    // menu entries: {name shown in the sidebar, permission needed to see it}
    static final String[][] NAV = {
        {"Dashboard", "VIEW_DASHBOARD"},
        {"Case Management", "VIEW_CASES"},
        {"Client Contacts", "VIEW_CLIENTS"},
        {"Schedule", "VIEW_SCHEDULE"},
        {"Documents", "VIEW_DOCUMENTS"},
        {"Billing and Payments", "VIEW_BILLING"},
        {"Lawyer Assignments", "VIEW_ASSIGNMENTS"},
        {"Reports", "VIEW_REPORTS"},
        {"User Accounts", "MANAGE_USERS"},
        {"Activity Log", "VIEW_LOG"},
    };

    private final CardLayout root = new CardLayout();
    private final LoginPanel login = new LoginPanel(this);
    private String visible = "login";   // the card on show: login or app

    // portal (rebuilt at every login so that each role gets its own menu and pages)
    private JPanel appCard;
    private LinkedHashMap<String, Page> pages;
    private LinkedHashMap<String, JLabel> navItems;
    private CardLayout pageLayout;
    private JPanel pageHost;
    private JLabel crumb;
    private String currentKey = "Dashboard";

    public Shell() {
        current = this;
        setLayout(root);
        add(login, "login");
        show("login");
        installShortcuts();
    }

    private void show(String card) {
        visible = card;
        root.show(this, card);
    }

    // =================================================================== session
    public void loginAs(User user) {
        Data.setCurrentUser(user);
        buildApp();
        show("app");
        navigate("Dashboard");
        Data.commit("Signed in");
        status("Signed in as " + user.getName() + " (" + user.roleTitle() + ")");
    }

    void logout() {
        Data.commit("Signed out");
        Data.setCurrentUser(null);
        if (appCard != null) { remove(appCard); appCard = null; }
        login.reset();
        status(" ");
        show("login");
        revalidate();
        repaint();
    }

    public void navigate(String key) {
        Page p = pages.get(key);
        if (p == null) return; // not part of this user's portal
        currentKey = key;
        pageLayout.show(pageHost, key);
        p.onShow();
        ArrayList<String> keys = new ArrayList<>(navItems.keySet());
        for (int ix = 0; ix < keys.size(); ix++) paintNav(navItems.get(keys.get(ix)), keys.get(ix).equals(key), false);
        crumb.setText(key);
        status(" ");
    }

    /** Opens a page and immediately its "add" form (used by the dashboard shortcut). */
    public Page page(String key) { return pages.get(key); }

    // >>> OOP | POLYMORPHISM: Returns many different classes (CasesPage, UsersPage ...) but all are handled as the one type: Page.
    private Page createPage(String key) {
        switch (key) {
            case "Dashboard":            return new DashboardPage();
            case "Case Management":      return new CasesPage();
            case "Client Contacts":      return new ClientsPage();
            case "Schedule":             return new SchedulePage();
            case "Documents":            return new DocumentsPage();
            case "Billing and Payments": return new BillingPage();
            case "Lawyer Assignments":   return new AssignmentsPage();
            case "Reports":              return new ReportsPage();
            case "User Accounts":        return new UsersPage();
            default:                     return new ActivityPage();
        }
    }

    // =================================================================== portal layout
    private void buildApp() {
        if (appCard != null) remove(appCard);
        User me = Data.getCurrentUser();
        pages = new LinkedHashMap<>();
        navItems = new LinkedHashMap<>();
        pageLayout = new CardLayout();
        pageHost = new JPanel(pageLayout);
        pageHost.setBackground(Ui.PAGE);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(4, 10, 4, 10));
        int number = 0;   // position in this user's menu: Ctrl+1 ... Ctrl+0
        for (int ix2 = 0; ix2 < NAV.length; ix2++) {
            String[] n = NAV[ix2];
            if (!me.can(n[1])) continue; // access control: only build what this role may open
            number++;
            Page page = createPage(n[0]);
            pages.put(n[0], page);
            pageHost.add(page, n[0]);
            JLabel item = navItem(n[0], n[0]);
            if (number <= 10) item.setToolTipText("Open " + n[0] + " (" + Ui.MOD + (number % 10) + ")");
            navItems.put(n[0], item);
            nav.add(item);
            nav.add(Box.createVerticalStrut(2));
        }

        appCard = new JPanel(new BorderLayout());
        appCard.add(buildSidebar(nav, me), BorderLayout.WEST);
        JPanel right = new JPanel(new BorderLayout());
        right.add(buildTopBar(me), BorderLayout.NORTH);
        right.add(pageHost, BorderLayout.CENTER);
        appCard.add(right, BorderLayout.CENTER);
        add(appCard, "app");
    }

    private JPanel buildSidebar(JPanel nav, User me) {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Ui.SIDE);
        side.setPreferredSize(new Dimension(230, 0));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Ui.brand(90), BorderLayout.NORTH);
        String portal = me.getRole().equals(User.ADMIN) ? "ADMIN PORTAL" : "LAW FIRM PORTAL";
        JLabel pl = Ui.label(portal, Ui.TAG, Ui.GRAY);
        pl.setBorder(new EmptyBorder(12, 16, 8, 8));
        top.add(pl, BorderLayout.CENTER);
        side.add(top, BorderLayout.NORTH);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(nav, BorderLayout.NORTH);
        side.add(wrap, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel out = new JPanel(new BorderLayout());
        out.setOpaque(false);
        out.setBorder(new EmptyBorder(0, 10, 8, 10));
        JLabel logoutItem = navItem("Logout", "Logout");
        logoutItem.setToolTipText("Sign out (" + Ui.MOD + "L)");
        out.add(logoutItem, BorderLayout.CENTER);
        bottom.add(out, BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new EmptyBorder(0, 6, 10, 6),
                new CompoundBorder(new LineBorder(Ui.LINE, 1), new EmptyBorder(8, 8, 8, 8))));
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(Ui.label(me.getName().replace("Atty. ", ""), Ui.BOLD, Ui.TEXT));
        text.add(Ui.label(me.roleTitle(), Ui.SMALL, Ui.GRAY));
        card.add(Ui.avatar(me.initials(), 30), BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        card.add(Ui.label("⋮", Ui.SUB, Ui.GRAY), BorderLayout.EAST);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText("Click to change your password or sign out");
        JPopupMenu menu = new JPopupMenu();
        JMenuItem pw = new JMenuItem("Change password");
        pw.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changePassword();
            }
        });
        JMenuItem lo = new JMenuItem("Logout");
        lo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logout();
            }
        });
        menu.add(pw);
        menu.add(lo);
        card.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { menu.show(card, 0, -menu.getPreferredSize().height); }
        });
        bottom.add(card, BorderLayout.SOUTH);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private JPanel buildTopBar(User me) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Ui.TOPBAR);
        bar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Ui.LINE), new EmptyBorder(0, 24, 0, 24)));
        bar.setPreferredSize(new Dimension(0, 34));
        String root = me.getRole().equals(User.ADMIN) ? "Admin Portal" : "Law Firm Portal";
        crumb = Ui.label("Dashboard", Ui.BOLD, Ui.TEXT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 9));
        left.setOpaque(false);
        left.add(Ui.label(root + "  >", Ui.SMALL, Ui.GRAY));
        left.add(crumb);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 9));
        right.setOpaque(false);
        right.add(statusLabel);
        right.add(Ui.label(me.getName(), Ui.BOLD, Ui.TEXT));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void changePassword() {
        JPasswordField cur = Ui.password(), n1 = Ui.password(), n2 = Ui.password();
        JPanel f = Ui.dialogForm();
        Ui.row(f, "Current password:", cur);
        Ui.row(f, "New password (min 6):", n1);
        Ui.row(f, "Repeat new password:", n2);
        if (!Ui.ask(this, "Change password", f)) return;
        User me = Data.getCurrentUser();
        String p1 = new String(n1.getPassword());
        if (!me.checkPassword(new String(cur.getPassword()))) { Ui.warn(this, "The current password is not correct."); return; }
        if (p1.length() < 6) { Ui.warn(this, "The new password must be at least 6 characters."); return; }
        if (!p1.equals(new String(n2.getPassword()))) { Ui.warn(this, "The two new passwords do not match."); return; }
        me.setPassword(p1);
        Data.commit("Changed own password");
        status("Password changed.");
    }

    // =================================================================== keyboard shortcuts
    private void installShortcuts() {
        ActionListener handler = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                shortcut(e.getActionCommand());
            }
        };
        int m = Ui.MASK;
        for (int i = 1; i <= 10; i++) {
            int key = i == 10 ? KeyEvent.VK_0 : KeyEvent.VK_1 + (i - 1);
            registerKeyboardAction(handler, "NAV" + i, KeyStroke.getKeyStroke(key, m), WHEN_IN_FOCUSED_WINDOW);
        }
        registerKeyboardAction(handler, "NEW", KeyStroke.getKeyStroke(KeyEvent.VK_N, m), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "EDIT", KeyStroke.getKeyStroke(KeyEvent.VK_E, m), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "FIND", KeyStroke.getKeyStroke(KeyEvent.VK_F, m), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "SAVE", KeyStroke.getKeyStroke(KeyEvent.VK_S, m), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "LOGOUT", KeyStroke.getKeyStroke(KeyEvent.VK_L, m), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "CANCEL", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "REFRESH", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "HELP", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "PREVPAGE", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, java.awt.event.InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(handler, "NEXTPAGE", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, java.awt.event.InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
    }

    private void shortcut(String cmd) {
        if (cmd == null) return;
        if (cmd.equals("HELP")) { showShortcuts(); return; }
        if (!visible.equals("app") || pages == null) return;   // the rest only works inside the portal
        // >>> OOP | POLYMORPHISM: p is a Page, but it really is a CasesPage, UsersPage ... When p.shortcutNew() runs, Java calls that page's own version.
        Page p = pages.get(currentKey);
        boolean form = p != null && p.isFormOpen();
        if (cmd.startsWith("NAV")) {
            ArrayList<String> keys = new ArrayList<>(pages.keySet());
            int i = Integer.parseInt(cmd.substring(3)) - 1;
            if (i < keys.size()) navigate(keys.get(i));
            return;
        }
        if (cmd.equals("LOGOUT")) { logout(); return; }
        if (p == null) return;
        switch (cmd) {
            case "REFRESH":  if (!form) { p.refresh(); status("Refreshed."); } break;
            case "SAVE":     if (form) p.shortcutSave(); break;
            case "CANCEL":   if (form) p.shortcutCancel(); break;
            case "NEW":      if (!form) p.shortcutNew(); break;
            case "EDIT":     if (!form) p.shortcutEdit(); break;
            case "FIND":     if (!form) p.shortcutFind(); break;
            case "PREVPAGE": if (!form) p.shortcutPage(-1); break;
            case "NEXTPAGE": if (!form) p.shortcutPage(1); break;
            default: break;
        }
    }

    /** F1: the list of shortcuts. */
    private void showShortcuts() {
        String m = Ui.MOD;
        String text =
            "EVERYWHERE\n"
          + "  F1                     This list\n"
          + "  " + m + "1 ... " + m + "9, " + m + "0     Open the 1st ... 10th menu entry\n"
          + "  F5                     Reload the page\n"
          + "  " + m + "L                 Sign out\n"
          + "\nTABLES\n"
          + "  " + m + "N                 New record (the blue + button)\n"
          + "  " + m + "E                 Edit the selected row\n"
          + "  Delete                 Delete / remove the selected row\n"
          + "  Enter                  Open the selected row\n"
          + "  " + m + "F                 Go to the search box (Esc clears it)\n"
          + "  Alt+Left / Alt+Right   Previous / next page of rows\n"
          + "\nFORMS\n"
          + "  " + m + "S                 Save\n"
          + "  Esc                    Cancel and go back\n";
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        JOptionPane.showMessageDialog(this, area, "Keyboard shortcuts", JOptionPane.PLAIN_MESSAGE);
    }

    // =================================================================== sidebar entry
    /** A flat menu entry; the selected one is filled blue. */
    private JLabel navItem(String text, final String key) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setFont(Ui.BOLD);
        l.setBorder(new EmptyBorder(0, 14, 0, 0));
        l.setPreferredSize(new Dimension(100, 36));
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        paintNav(l, false, false);
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { paintNav(l, Boolean.TRUE.equals(l.getClientProperty("sel")), true); }
            @Override public void mouseExited(MouseEvent e) { paintNav(l, Boolean.TRUE.equals(l.getClientProperty("sel")), false); }
            @Override public void mousePressed(MouseEvent e) {
                if (key.equals("Logout")) logout();
                else navigate(key);
            }
        });
        return l;
    }

    private static void paintNav(JLabel l, boolean selected, boolean hover) {
        l.putClientProperty("sel", selected);
        l.setBackground(selected ? Ui.BLUE : hover ? Ui.HEAD : Ui.SIDE);
        l.setForeground(selected ? Color.WHITE : Ui.TEXT);
    }
}
