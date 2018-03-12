/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Everything that gets written here also gets written to the defined second output
 * @author Lutz Fischer <lfischer@staffmail.ed.ac.uk>
 */
public class OutputSplitter extends PrintStream{
    PrintStream orig;

    public OutputSplitter(PrintStream orig, File out) throws FileNotFoundException {
        super(out);
        this.orig = orig;
    }

    public OutputSplitter(PrintStream orig, String fileName) throws FileNotFoundException {
        super(fileName);
        this.orig = orig;
    }

    public OutputSplitter(PrintStream orig, OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
        this.orig = orig;
    }

    @Override
    public boolean checkError() {
        return super.checkError() || orig.checkError();
    }

    @Override
    public void close() {
        orig.close();
        super.close(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void flush() {
        orig.flush();
        super.flush(); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public void write(byte[] b) throws IOException {
        orig.write(b);
        super.write(b); //To change body of generated methods, choose Tools | Templates.
    }

    
    
    
    
    
    
    
    
    
}
