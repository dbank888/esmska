/*
 * SMSSender.java
 *
 * Created on 6. červenec 2007, 17:55
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package esmska;

import java.util.ArrayList;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import operators.Operator;

/**
 *
 * @author ripper
 */
public class SMSSender {
    private ArrayList<SMS> smsQueue;
    private boolean running; // sending sms in this moment
    private boolean paused; // queue paused
    private SMSWorker smsWorker;
    private Main parent;
    
    /** Creates a new instance of SMSSender */
    public SMSSender(ArrayList<SMS> smsQueue, Main parent) {
        if (smsQueue == null)
            throw new NullPointerException("smsQueue");
        this.smsQueue = smsQueue;
        this.parent = parent;
    }
    
    public void announceNewSMS() {
        prepareSending();
    }
    
    private void prepareSending() {
        if (!isPaused() && !running && !smsQueue.isEmpty()) {
            running = true;
            SMS sms = smsQueue.get(0);
            sms.setStatus(SMS.Status.PROBLEMATIC);
            parent.setTaskRunning(true);
            parent.printStatusMessage("Posílám zprávu pro " + sms.getNumber()
            + " (" + sms.getOperator() + ") ...");
            
            //send in worker thread
            smsWorker = new SMSWorker(sms);
            smsWorker.execute();
        }
    }
    
    private void finishedSending(SMS sms) {
        if (sms.getStatus() == SMS.Status.SENT_OK) {
            smsQueue.remove(sms);
            parent.printStatusMessage("Zpráva pro " + sms.getNumber()
            + " byla úspěšně odeslána.");
        }
        if (sms.getStatus() == SMS.Status.PROBLEMATIC) {
            parent.printStatusMessage("Zprávu pro " + sms.getNumber()
            + " se nepodařilo odeslat!");
            parent.pauseSMSQueue();
            
            JOptionPane.showMessageDialog(parent,
                    "Zprávu se nepovedlo odeslat!\n\nDůvod: " + sms.getErrMsg(),
                    "Chyba odesílání",JOptionPane.WARNING_MESSAGE);
        }
        parent.smsQueueChanged();
        parent.setTaskRunning(false);
        running = false;
        prepareSending();
    }
    
    private class SMSWorker extends SwingWorker<Void, Void> {
        private SMS sms;
        
        public SMSWorker(SMS sms) {
            super();
            this.sms = sms;
        }
        
        protected void done() {
            finishedSending(sms);
        }
        
        protected Void doInBackground() throws Exception {
            Operator operator = sms.getOperator();
            sms.setImage(operator.getSecurityImage());
            
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    JPanel panel = new JPanel();
                    JLabel label = new JLabel("Opište kód z obrázku:",
                            new ImageIcon(sms.getImage()), JLabel.CENTER);
                    label.setHorizontalTextPosition(JLabel.CENTER);
                    label.setVerticalTextPosition(JLabel.TOP);
                    panel.add(label);
                    String imageCode = JOptionPane.showInputDialog(parent, panel, "Kontrolní kód",
                            JOptionPane.QUESTION_MESSAGE);
                    if (imageCode != null)
                        sms.setImageCode(imageCode);
                }
            });
            
            boolean success = operator.send(sms);
            if (success)
                sms.setStatus(SMS.Status.SENT_OK);
            
            return null;
        }
        
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused == false)
            prepareSending();
    }
}
