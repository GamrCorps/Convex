package src.gamrcorps.convex;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Keyboard extends JDialog {
    private JPanel contentPane;

    public Keyboard() {
        setTitle("Keyboard");
        setContentPane(contentPane);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        contentPane.setLayout(new GridLayout(6,16));
        for (int i = 160; i < 256; i++){
            JButton temp = new JButton(""+(char)i);
            temp.setFont(new Font("Courier New", Font.PLAIN, 20));
            temp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    IDE.ide.textArea1.setText(IDE.ide.textArea1.getText()+temp.getText());
                }
            });
            contentPane.add(temp);
        }
    }
}
