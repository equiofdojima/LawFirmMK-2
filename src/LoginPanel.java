import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import static java.awt.Color.WHITE;

/**
 * The sign-in screen: a dark panel with the firm's message on the left, the form on the right.
 * Three wrong passwords in a row block that account: a lawyer stays blocked until an administrator
 * unblocks it (User Accounts > Block / Unblock); an administrator is only locked for 30 seconds.
 */
public class LoginPanel extends JPanel {
    private static final Color DARK = new Color(16, 35, 60);          // left panel
    private static final Color RIGHT = new Color(238, 238, 238);      // right panel
    private static final Color GOLD_ON_DARK = new Color(184, 146, 70);
    private static final Color TEXT_ON_DARK = new Color(190, 200, 215);

    private final Shell shell;
    private JTextField userField;
    private JPasswordField passField;
    private JLabel loginMessage;
    private JButton signIn;
    private JCheckBox showPassword;
    private char hiddenChar;   // the dot that hides the password

    public LoginPanel(Shell shell) {
        this.shell = shell;
        buildLogin();
    }

    /** Empties the fields (called after logout). */
    public void reset() {
        userField.setText("");
        passField.setText("");
        showPassword.setSelected(false);
        passField.setEchoChar(hiddenChar);
        loginMessage.setText(" ");
        userField.requestFocusInWindow();
    }

    private void buildLogin() {
        // two halves side by side: 43% dark panel, 57% form
        setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;

        JPanel left = buildLeft();
        left.setPreferredSize(new Dimension(430, 600));
        g.gridx = 0; g.weightx = 0.43;
        add(left, g);

        JPanel right = buildRight();
        right.setPreferredSize(new Dimension(570, 600));
        g.gridx = 1; g.weightx = 0.57;
        add(right, g);
    }

    // =================================================================== left: the firm's message
    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout()) {
            // two very faint diagonal lines as decoration
            @Override
            protected void paintComponent(Graphics gr) {
                super.paintComponent(gr);
                Graphics2D g2 = (Graphics2D) gr.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 16));
                g2.drawLine(getWidth() / 3, getHeight(), getWidth(), getHeight() / 4);
                g2.drawLine(getWidth() / 2, getHeight(), getWidth(), getHeight() / 2);
                g2.dispose();
            }
        };
        left.setBackground(DARK);
        left.setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));

        // top: logo
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        brand.add(new ScalesIcon());
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        names.add(Ui.label("Del Rosario", new Font("Serif", Font.PLAIN, 19), WHITE));
        names.add(Ui.label("LAW OFFICES", Ui.TAG, TEXT_ON_DARK));
        brand.add(names);
        left.add(brand, BorderLayout.NORTH);

        // middle: the message
        JPanel message = new JPanel();
        message.setOpaque(false);
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel eyebrow = Ui.label("LEGAL PRACTICE MANAGEMENT", Ui.TAG.deriveFont(15f), GOLD_ON_DARK);
        JLabel heading = Ui.label("<html>Clarity in every<br>case.</html>", Ui.BOLD.deriveFont(40f), Color.WHITE);
        JLabel text = Ui.label("<html>A focused workspace for your matters, clients,<br>schedules, and financial records.</html>", Ui.PLAIN.deriveFont(18.5f), TEXT_ON_DARK);
        eyebrow.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        message.add(eyebrow);
        message.add(heading);
        message.add(text);
        JPanel middle = new JPanel(new GridBagLayout());   // centres the block vertically
        middle.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        middle.add(message, c);
        left.add(middle, BorderLayout.CENTER);

        // bottom: small footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(Ui.label("DEL ROSARIO LAW OFFICES", Ui.TAG, TEXT_ON_DARK), BorderLayout.WEST);
        footer.add(Ui.label("Makati City \u00B7 Philippines", Ui.SMALL, TEXT_ON_DARK), BorderLayout.EAST);
        left.add(footer, BorderLayout.SOUTH);
        return left;
    }

    // =================================================================== right: the form
    private JPanel buildRight() {
        JPanel right = new JPanel(new GridBagLayout());   // centres the form
        right.setBackground(RIGHT);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setPreferredSize(new Dimension(400, 400));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;

        userField = Ui.field("");
        userField.setFont(Ui.SMALL.deriveFont(13.5f));
        userField.setPreferredSize(new Dimension(400, 38));
        userField.setToolTipText("Your username or the email address of your account");
        passField = Ui.password();
        passField.setPreferredSize(new Dimension(400, 38));
        passField.setFont(Ui.SMALL.deriveFont(13.5f));
        passField.setToolTipText("Your password. " + User.MAX_ATTEMPTS + " wrong tries in a row block the account");
        hiddenChar = passField.getEchoChar();
        showPassword = new JCheckBox("Show password");
        showPassword.setOpaque(false);
        showPassword.setFont(Ui.SMALL);
        showPassword.setFocusPainted(false);
        showPassword.setToolTipText("Show or hide the characters you type");
        showPassword.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // echo char 0 means "show the real text"
                passField.setEchoChar(showPassword.isSelected() ? (char) 0 : hiddenChar);
            }
        });
        signIn = Ui.primary("Sign in to LawFirm Portal");
        signIn.setFont(Ui.SUB);
        signIn.setPreferredSize(new Dimension(400, 42));
        loginMessage = Ui.label(" ", Ui.BOLD, Ui.RED);

        int y = 0;
        g.gridy = y++; g.insets = new Insets(0, 0, 14, 0);
        form.add(Ui.label("AUTHORIZED PERSONNEL", Ui.TAG, Ui.GOLD), g);
        g.gridy = y++; g.insets = new Insets(0, 0, 8, 0);
        form.add(Ui.label("Welcome back", Ui.BOLD.deriveFont(28f), new Color(20, 28, 40)), g);
        g.gridy = y++; g.insets = new Insets(0, 0, 22, 0);
        form.add(Ui.label("Enter your credentials to access the firm's workspace.", Ui.SMALL, Ui.GRAY), g);
        g.gridy = y++; g.insets = new Insets(0, 0, 5, 0);
        form.add(Ui.label("Staff ID or email", Ui.SMALL, Ui.TEXT), g);
        g.gridy = y++; g.insets = new Insets(0, 0, 16, 0);
        form.add(userField, g);
        g.gridy = y++; g.insets = new Insets(0, 0, 5, 0);
        form.add(Ui.label("Password", Ui.SMALL, Ui.TEXT), g);
        g.gridy = y++; g.insets = new Insets(0, 0, 6, 0);
        form.add(passField, g);
        g.gridy = y++; g.insets = new Insets(0, 0, 20, 0);
        form.add(showPassword, g);
        g.gridy = y++; g.insets = new Insets(0, 0, 10, 0);
        form.add(signIn, g);
        g.gridy = y++; g.insets = new Insets(0, 0, 0, 0);
        form.add(loginMessage, g);
        if (Data.isSeeded()) {
            g.gridy = y++; g.insets = new Insets(14, 0, 0, 0);
            form.add(Ui.label("<html>First run \u2014 demo accounts:<br>admin / admin123 &nbsp; reyes / lawyer123</html>",
                    Ui.SMALL, Ui.GRAY), g);
        }
        right.add(form);

        // the button and the Enter key of the password box do the same thing
        ActionListener loginListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doLogin();
            }
        };
        signIn.addActionListener(loginListener);
        passField.addActionListener(loginListener);
        userField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                passField.requestFocusInWindow();
            }
        });
        return right;
    }

    // =================================================================== signing in
    /** The username can be typed as the email address of the account too. */
    private String usernameFor(String typed) {
        if (!typed.contains("@")) return typed;
        for (int i = 0; i < Data.getUsers().size(); i++) {
            User u = Data.getUsers().get(i);
            if (u.getEmail().equalsIgnoreCase(typed)) return u.getUsername();
        }
        return typed;   // unknown email: Data.login() will say the credentials are invalid
    }

    private void doLogin() {
        String typed = userField.getText().trim();
        String password = new String(passField.getPassword());
        if (typed.isEmpty() || password.isEmpty()) {
            loginMessage.setText("Enter your username (or email) and password.");
            return;
        }
        User u = Data.login(usernameFor(typed), password);
        if (u != null) {
            loginMessage.setText(" ");
            shell.loginAs(u);
            return;
        }
        passField.setText("");
        loginMessage.setText("<html>" + Data.getLoginError() + "</html>");
    }

    // =================================================================== the little scales logo
    private static class ScalesIcon extends JComponent {
        ScalesIcon() {
            setPreferredSize(new Dimension(40, 40));
        }

        @Override
        protected void paintComponent(Graphics gr) {
            Graphics2D g = (Graphics2D) gr.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, 70));
            g.drawRect(0, 0, 39, 39);                       // the square frame
            g.setColor(GOLD_ON_DARK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(20, 10, 20, 29);                     // post
            g.drawLine(14, 29, 26, 29);                     // base
            g.drawLine(9, 13, 31, 13);                      // beam
            g.drawLine(9, 13, 5, 22);                       // left strings and pan
            g.drawLine(9, 13, 13, 22);
            g.drawArc(5, 19, 8, 6, 180, 180);
            g.drawLine(31, 13, 27, 22);                     // right strings and pan
            g.drawLine(31, 13, 35, 22);
            g.drawArc(27, 19, 8, 6, 180, 180);
            g.dispose();
        }
    }
}
