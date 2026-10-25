import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.*;

/** One screen of the portal. A page shows its list view, or a full-page form on top of it. */
// >>> OOP | INHERITANCE: Page IS-A JPanel (inherits from Swing) and is abstract: every screen of the portal extends it.
public abstract class Page extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel formHost = new JPanel(new BorderLayout());
    // >>> OOP | ENCAPSULATION: Private state: other classes use openForm(), closeForm() and isFormOpen().
    private Form activeForm;   // the form that is open on this page (null while the list is shown)

    protected Page() {
        setLayout(cards);
        setBackground(Ui.PAGE);
        formHost.setOpaque(false);
        add(formHost, "form");
    }

    protected void setList(JComponent c) {
        add(c, "list");
        cards.show(this, "list");
    }

    /** Reload the data shown on the list view. */
    // >>> OOP | POLYMORPHISM: Each page reloads itself differently. The window just calls refresh() on whatever page it has.
    public abstract void refresh();

    /** Called every time the user navigates to this page. */
    public void onShow() {
        activeForm = null;
        cards.show(this, "list");
        refresh();
    }

    public void openForm(Form f) {
        activeForm = f;
        formHost.removeAll();
        formHost.add(f, BorderLayout.CENTER);
        cards.show(this, "form");
        formHost.revalidate();
        formHost.repaint();
        f.focusFirst();
    }

    public void closeForm() {
        activeForm = null;
        cards.show(this, "list");
        refresh();
    }

    public boolean isFormOpen() { return activeForm != null; }

    // ---- keyboard shortcuts: the window (Shell) calls these on the page that is showing ----
    /** Ctrl+S: save the open form. */
    public void shortcutSave() { if (activeForm != null) activeForm.submit(); }

    /** Esc: leave the open form without saving. */
    public void shortcutCancel() { if (activeForm != null) closeForm(); }

    /** Ctrl+N, Ctrl+E, Delete, Ctrl+F, Alt+Left/Right: only the table pages react to these. */
    // >>> OOP | POLYMORPHISM: Default: do nothing. TablePage overrides these methods, so the SAME call works on every kind of page.
    public void shortcutNew() {}
    public void shortcutEdit() {}
    public void shortcutDelete() {}
    public void shortcutFind() {}
    public void shortcutPage(int delta) {}

    protected void warn(String message) {
        Ui.warn(this, message);
    }

    protected void status(String message) {
        Shell.status(message);
    }
}

/**
 * A full-page form: a white card with titled sections and labelled inputs in three columns,
 * ending in Cancel / Save buttons. The save callback returns an error message, or null when saved.
 */
// >>> OOP | INHERITANCE: Form IS-A JPanel.
class Form extends JPanel {
    private final JPanel grid = new JPanel(new GridBagLayout());
    private final JLabel error = Ui.label(" ", Ui.BOLD, Ui.RED);
    private final Page page;
    private final Saver onSave;
    private int row = 0, col = 0;
    private JComponent first;

    public Form(Page page, String eyebrow, String title, String saveText, Saver onSave) {
        super(new BorderLayout());
        this.page = page;
        this.onSave = onSave;
        setOpaque(false);
        setBorder(new EmptyBorder(22, 32, 22, 32));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new LineBorder(Ui.LINE, 1), new EmptyBorder(18, 24, 18, 24)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel e = Ui.eyebrow(eyebrow);
        e.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = Ui.label(title, Ui.HEAD_F, Ui.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(e);
        head.add(t);
        head.setBorder(new EmptyBorder(0, 0, 10, 0));
        card.add(head, BorderLayout.NORTH);

        grid.setOpaque(false);
        for (int c = 0; c < 3; c++) { // invisible first row keeps the three columns equally wide
            GridBagConstraints st = new GridBagConstraints();
            st.gridx = c; st.gridy = 0; st.weightx = 1; st.fill = GridBagConstraints.HORIZONTAL;
            JPanel strut = new JPanel();
            strut.setOpaque(false);
            strut.setPreferredSize(new Dimension(240, 0));
            grid.add(strut, st);
        }
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(grid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(gridWrap);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        card.add(sp, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(10, 0, 0, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        Btn cancel = Ui.secondary("Cancel");
        Btn save = Ui.primary(saveText);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                page.closeForm();
            }
        });
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                submit();
            }
        });
        cancel.setToolTipText("Discard this form and go back (Esc)");
        save.setToolTipText("Save and close (" + Ui.MOD + "S)");
        buttons.add(cancel);
        buttons.add(save);
        foot.add(error, BorderLayout.WEST);
        foot.add(buttons, BorderLayout.EAST);
        card.add(foot, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
    }

    /** What the Save button does (also Ctrl+S): run the saver, close on success, show its message otherwise. */
    public void submit() {
        String msg = onSave.save();
        if (msg == null) page.closeForm();
        else error.setText(msg);
    }

    /** Starts a titled group of fields (a gold caps heading with a line under it). */
    public Form section(String title) {
        if (col != 0) { row++; col = 0; }
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row * 2 + 1; g.gridwidth = 3; g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(row == 0 ? 0 : 14, 0, 6, 0);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new MatteBorder(0, 0, 1, 0, Ui.LINE));
        JLabel l = Ui.eyebrow(title.toUpperCase());
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        p.add(l, BorderLayout.WEST);
        grid.add(p, g);
        row++;
        return this;
    }

    /** Adds a labelled input that takes `span` of the three columns. */
    public JComponent add(String label, JComponent input, int span) {
        if (col + span > 3) { row++; col = 0; }
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = col; g.gridy = row * 2 + 1; g.gridwidth = span; g.weightx = span;
        g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(8, 0, 2, 16);
        if (input.getToolTipText() == null) {
            boolean required = label.contains("*");
            input.setToolTipText(label.replace("*", "").trim() + (required ? " (required)" : " (optional)"));
        }
        grid.add(Ui.label(label, Ui.SMALL, Ui.GRAY), g);
        GridBagConstraints g2 = (GridBagConstraints) g.clone();
        g2.gridy = row * 2 + 2;
        g2.insets = new Insets(0, 0, 4, 16);
        if (input instanceof JTextArea) {
            JScrollPane sp = Ui.scroll(input);
            sp.setPreferredSize(new Dimension(100, 70));
            grid.add(sp, g2);
        } else {
            input.setPreferredSize(new Dimension(100, 30));
            grid.add(input, g2);
        }
        if (first == null && !(input instanceof JTextArea) && input.isEnabled()) first = input;
        col += span;
        if (col >= 3) { row++; col = 0; }
        return input;
    }

    /** A one-column field (the 3-argument version above can span more columns). */
    public JComponent add(String label, JComponent input) {
        return add(label, input, 1);
    }

    public void focusFirst() {
        if (first != null) SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                first.requestFocusInWindow();
            }
        });
    }
}

/** What a form does when "Save" is pressed: returns an error message, or null when everything was saved. */
// >>> OOP | ABSTRACTION: Interface: only the contract ("a save() that returns an error message or null"). Each page supplies its own version.
interface Saver {
    String save();
}

/** Reloads a page's table whenever a filter box changes. */
// >>> OOP | ABSTRACTION: Implements a Swing interface: it promises an actionPerformed() method, and Swing calls it without knowing this class.
class RefreshListener implements ActionListener {
    private final Page page;

    RefreshListener(Page page) {
        this.page = page;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        page.refresh();
    }
}
