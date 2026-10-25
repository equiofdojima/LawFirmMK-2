import javax.swing.*;
import java.awt.Dimension;

public class LawFirmVol2 {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Data.init();
                JFrame frame = new JFrame(Case.getFirmName() + " — Law Firm Portal");
                frame.setContentPane(new Shell());
                frame.setSize(1280, 760);
                frame.setMinimumSize(new Dimension(1100, 640));
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
}
