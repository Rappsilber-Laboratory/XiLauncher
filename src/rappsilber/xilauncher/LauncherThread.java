/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import rappsilber.xilauncher.config.Config;
import rappsilber.xilauncher.config.Config.Queue;
import rappsilber.xilauncher.log.ProcessLogger;
import rappsilber.xilauncher.processes.ProcessLauncher;

/**
 *
 * @author lfischer
 */
public class LauncherThread extends Thread{
    Config.Queue queue;
    Config  conf;
    String logDir;

    public LauncherThread(Queue queue, Config conf, String logDir) {
        this.queue = queue;
        this.conf = conf;
        this.logDir = logDir;
    }
    
    
    
    
    
    @Override
    public void run() {
        queue.started = true;
        SimpleDateFormat logDate = new SimpleDateFormat("yyyyMMdd_HHmmss");
        ObjectWrapper<String> name = new ObjectWrapper<String>();
        ObjectWrapper<Integer> search = new ObjectWrapper<Integer>();
        ObjectWrapper<Config.DBConnection> connection = new ObjectWrapper<Config.DBConnection>();
        boolean oldEnabled = true;
        System.out.println("queue " + queue.name + " started");
        int smallSearchesRun =0;
        while (!queue.stop) {
            String[] defargs;
            Integer maxSize;
            Integer maxPeakListSize;
            boolean enabled;
            String queuename;
            Integer priouser;
            conf.recoverServers();
            
            synchronized (queue) {
                defargs = queue.Args;
                maxSize = queue.maxFastaSize;
                enabled = queue.enabled;
                queuename = queue.name;
                maxPeakListSize = queue.recomendedMaxPeakList;
                priouser = queue.prioUserID;
            }
            
            if (enabled) {
                if (!oldEnabled) {
                    System.out.println("queue " + queuename + " enabled");
                }
                
                String[] args = new String[defargs.length + 2];
                boolean haveRun = false;
                
                if (smallSearchesRun <5) {
                    if (maxPeakListSize!= null && maxPeakListSize > 0) {
                        haveRun = XiLauncher.getNextRun(maxSize, maxPeakListSize, conf, connection, search, name,priouser);
                        if (haveRun) {
                            smallSearchesRun+=1;
                        }
                    }
                } else {
                    smallSearchesRun = 0;
                }
                
                if (!haveRun)
                    haveRun = XiLauncher.getNextRun(maxSize, conf, connection, search, name,priouser);
                
               
                if (haveRun) {
                    // ok we have a run make sure nobody else is starting it.
                    haveRun = XiLauncher.lockRun(conf, connection, search, name);
                }
                
                if (haveRun) {
                    if (System.getProperty("TRYRUN","FALSE").contentEquals("TRUE"))
                        continue;
                    for (int a = 0 ; a< defargs.length; a++) {
                        args[a] = defargs[a].replace("%DB%", connection.getValue().getConnectionString());
                        args[a] = args[a].replace("%DBUSER%", connection.getValue().getConnectionString());
                    }
    //                args[defaultArgs.length + 2] = "rappsilber.applications.XiDB";
                    args[defargs.length + 0] = search.toString();
                    args[defargs.length + 1] = name.toString();
                    
                    ProcessLauncher launcher = new ProcessLauncher(args);
                    Calendar c = Calendar.getInstance();

                    String sdate = logDate.format(c.getTime());
                    ProcessLogger plout = null;
                    try {
                        String n = name.v.replaceAll("[^a-zA-Z0-9\\._]","_");
                        if (n.length() > 30) {
                            n= n.substring(0, 30);
                        }
                        plout = new ProcessLogger(logDir + sdate+"_"+connection.v.getConnectionString().replaceAll("[^a-zA-Z0-9\\._]","_")+"_Search_" + search+"_"+ n + ".log", true);
                        plout.standardOutput("\n"+sdate+": Start Xi on database " + connection + " starting serach " + name + "(" + search + ")\n");
                        launcher.addOutputListener(plout);
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(XiLauncher.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    launcher.launch();
                    if (plout != null)  {
                        sdate = logDate.format(c.getTime());
                        plout.standardOutput("\n"+sdate+": finished Xi on database " + connection + " starting serach " + name + "(" + search + ")\n");
                    }

                }
            } else {
                if (!oldEnabled) {
                    System.out.println("queue " + queuename + " disabled");
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                
            }
            
        }
        synchronized(queue) {
            queue.started = false;
        }
        
        
    }
}
