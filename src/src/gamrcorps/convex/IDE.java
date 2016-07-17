package src.gamrcorps.convex;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by Matthew on 3/10/2016.
 */
public class IDE {
    private JPanel mainPanel;
    public JTextArea textArea1;
    private JTextArea textArea2;
    private JTextField textField1;
    private JTextArea textArea3;
    private JButton runCodeButton;
    private JLabel bytesLabel;
    public static IDE ide;

    public IDE() {
        runCodeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                textArea3.setText("");
                Convex x = new Convex(new ScannerIn(new Scanner(textArea2.getText())), new TextAreaOut(textArea3), new TextAreaOut(textArea3, "[ERROR] "));
                x.setArgs(getArgs(textField1.getText()));
                for (String s : x.getArgs()) {
                    if (s.length()>0&&(s.startsWith("[")||s.startsWith("\"")||s.startsWith("{")||s.substring(0,1).matches("[0-9]"))) {
                        x.runCode(Block.parse(new StringReader(s), false), false);
                    } else {
                        if (s.length()!=0)
                            x.push(Conv.strToList(s));
                    }
                }
                final String s = textArea1.getText();
                final Block b = Block.parse(new StringReader(s), false);
                x.runCode(b, true);
                x.clear();
            }
        });
        textArea1.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                bytesLabel.setText(textArea1.getText().length() + " bytes");
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                bytesLabel.setText(textArea1.getText().length() + " bytes");
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                bytesLabel.setText(textArea1.getText().length() + " bytes");
            }
        });
        textArea1.setFont(new Font("Courier New", Font.PLAIN, 20));
        textArea2.setFont(new Font("Courier New", Font.PLAIN, 20));
        textArea3.setFont(new Font("Courier New", Font.PLAIN, 20));
        textField1.setFont(new Font("Courier New", Font.PLAIN, 20));
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Convex IDE");
        frame.setContentPane((ide = new IDE()).mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800,600);
        ide.mainPanel.setSize(800,600);
        frame.pack();
        frame.setVisible(true);
        Keyboard dialog = new Keyboard();
        dialog.pack();
        dialog.setVisible(true);
    }

    public static String[] getArgs (String input) {
        List<String> strList = new ArrayList<>();
        String temp = "";
        int pointer = 0;
        boolean inString = false;
        while (pointer < input.length()) {
            if (input.charAt(pointer) == ' ' && !inString) {
                strList.add(temp);
                temp = "";
                pointer++;
            } else if (input.charAt(pointer) == '"' && !(pointer == 0 ||input.charAt(pointer-1) != '\\')) {
                inString = !inString;
                pointer++;
            } else if (input.charAt(pointer) == '\\' && input.charAt(pointer-1) != '\\') {
                pointer++;
            } else {
                temp += input.charAt(pointer) + "";
                pointer++;
            }
        }
        strList.add(temp);
        return strList.toArray(new String[0]);
    }
}