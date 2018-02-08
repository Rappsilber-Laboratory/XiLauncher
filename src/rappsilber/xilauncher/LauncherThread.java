/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher;

import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.sql.Statement;
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
//        ObjectWrapper<String> name = new ObjectWrapper<String>();
//        ObjectWrapper<Integer> search = new ObjectWrapper<Integer>();
//        ObjectWrapper<Config.DBConnection> connection = new ObjectWrapper<Config.DBConnection>();
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
                XiSearch nextRun = null; 
                        
                if (smallSearchesRun <5) {
                    if (maxPeakListSize!= null && maxPeakListSize > 0) {
                        nextRun  = XiLauncher.getNextRun(maxSize, maxPeakListSize, conf,priouser);
                        if (nextRun != null) {
                            smallSearchesRun+=1;
                        }
                    }
                } else {
                    smallSearchesRun = 0;
                }
                
                if (nextRun == null)
                    nextRun = XiLauncher.getNextRun(maxSize, conf, priouser);
                
                boolean haveRun = false;
                if (nextRun != null) {
                    // ok we have a run make sure nobody else is starting it.
                    haveRun = XiLauncher.lockRun(conf, nextRun);
                }
                
                if (haveRun) {
                    // we have the run and canstart xi now
                    if (System.getProperty("TRYRUN","FALSE").contentEquals("TRUE"))
                        continue;
                    
                    Config.JarDefinition j = conf.defaultJar;
                    if (nextRun.xiVersion != null && !nextRun.xiVersion.isEmpty()) {
                        j = j.getForVersion(nextRun.xiVersion);
                        if (j.getFile() == null) {
                            try {
                                Statement st = nextRun.connection.getConnection().createStatement();
                                st.execute("UPDATE search SET status = 'XiLauncher: Requested Xi version not found' WHERE  id = " + nextRun.search +";");
                                st.close();
                                nextRun.connection.getConnection().commit();
                                continue;
                            } catch (SQLException ex) {
                                Logger.getLogger(LauncherThread.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    
                    
                    for (int a = 0 ; a< defargs.length; a++) {
                        args[a] = defargs[a].replace("%DB%", nextRun.connection.getConnectionString());
                        args[a] = args[a].replace("%DBUSER%", nextRun.connection.getUser());
                        args[a] = args[a].replace("%DBPASS%", nextRun.connection.getPassword());
                        args[a] = args[a].replace("%JAR%", j.getFile());
                    }
    //                args[defaultArgs.length + 2] = "rappsilber.applications.XiDB";
                    args[defargs.length + 0] = nextRun.search.toString();
                    args[defargs.length + 1] = nextRun.name;
                    
                    ProcessLauncher launcher = new ProcessLauncher(args);
                    Calendar c = Calendar.getInstance();

                    String sdate = logDate.format(c.getTime());
                    ProcessLogger plout = null;
                    try {
                        String n = nextRun.name.replaceAll("[^a-zA-Z0-9\\._]","_");
                        if (n.length() > 30) {
                            n= n.substring(0, 30);
                        }
                        plout = new ProcessLogger(logDir + sdate+"_"+nextRun.connection.getConnectionString().replaceAll("[^a-zA-Z0-9\\._]","_")+"_Search_" + nextRun.search +"_"+ n + ".log", true);
                        plout.standardOutput("\n"+sdate+": Start Xi on database " + nextRun.connection + " starting serach " + nextRun.name + "(" + nextRun.search + ")\n");
                        launcher.addOutputListener(plout);
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(XiLauncher.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    launcher.launch();
                    if (plout != null)  {
                        sdate = logDate.format(c.getTime());
                        plout.standardOutput("\n"+sdate+": finished Xi on database " + nextRun.connection + " starting serach " + nextRun.name + "(" + nextRun.search + ")\n");
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
