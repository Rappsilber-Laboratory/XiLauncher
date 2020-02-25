/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import rappsilber.xilauncher.processes.ProcessLauncher;

/**
 *
 * @author lfischer
 */
public class ProcessLogger implements ProcessLauncher.OutputListener{

    private boolean toSTDOUT = true;
    private PrintWriter logOut = null;
    private StringBuffer stdout = new StringBuffer(1000);

    public ProcessLogger() {
        toSTDOUT = true;
    }
    
    public ProcessLogger(PrintWriter logOut, boolean toSTDOUT) {
        this.toSTDOUT = toSTDOUT;
        this.logOut = logOut;
    }
    
    public ProcessLogger(String logfile, boolean toSTDOUT) throws FileNotFoundException {
        this.toSTDOUT = toSTDOUT;
        File f = new File(logfile);
        this.logOut = new PrintWriter(f);
    }
    
   
    public void standardOutput(String s) {
        standardOutput(s.toCharArray());
    }

    public void errorOutput(String s) {
        errorOutput(s.toCharArray());
    }
    
    public PrintWriter getOutput() {
        return logOut;
    }
    
    

    
    public void close() {
        if (this.logOut != null) {
            this.logOut.flush();
            this.logOut.close();
        }
        
    }
    
    @Override
    public void finalize() {
        if (toSTDOUT) {
            System.out.println(stdout.toString());
        }
        if (logOut != null)
            logOut.close();
    }
    
    
    

    public synchronized void standardOutput(char[] output) {
        if (logOut != null) {
            for (int i = 0; i<output.length; i++) {
                logOut.append(output[i]);
                if (output[i] == '\n')
                    logOut.flush();
            }
            
        }
        if (toSTDOUT) {
            for (int i = 0; i<output.length; i++) {
                if (output[i] == '\n') {
                    System.out.println(stdout.toString());
                    stdout.setLength(0);
                } else if (output[i] != '\r') {
                    if (stdout.length() >1000) {
                        System.out.print(stdout.toString());
                        stdout.setLength(0);
                    }
                    stdout.append(output[i]);
                }
                    
            }
        }
    }

    public synchronized void errorOutput(char[] output) {
        if (logOut != null) {
            for (int i = 0; i<output.length; i++)
                logOut.append(output[i]);
            logOut.flush();
        }
        
        for (int i = 0; i<output.length; i++) {
            if (output[i] == '\n') {
                System.out.println(stdout.toString());
                stdout.setLength(0);
            } else if (output[i] != '\r') {
                if (stdout.length() >1000) {
                    System.out.print(stdout.toString());
                    stdout.setLength(0);
                }
                stdout.append(output[i]);
            }

        }
    }
    
}
