package src.gamrcorps.convex;

import javax.swing.*;

/**
 * Created by Matthew on 3/10/2016.
 */
public class TextAreaOut extends Out {
    private JTextArea jTextArea;
    private String prefix = "";

    public TextAreaOut (JTextArea jta) {
        jTextArea = jta;
    }

    public TextAreaOut (JTextArea jta, String prefix) {
        jTextArea = jta;
        this.prefix = prefix;
    }

    @Override
    public void print(Object o) {
        jTextArea.setText(jTextArea.getText()+String.valueOf(o));
    }

    @Override
    public void println() {
        jTextArea.setText(jTextArea.getText()+
                //prefix+
                "\n");
    }

    @Override
    public void println(Object o) {
        jTextArea.setText(jTextArea.getText()+
                //prefix+
                String.valueOf(o)+"\n");
    }
}
