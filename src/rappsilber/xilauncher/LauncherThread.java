/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher;

import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.ResultSet;
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
        Logger.getLogger(this.getClass().getName()).log(Level.INFO,"queue " + queue.name + " started");
        int smallSearchesRun =0;
        while (!queue.stop) {
            String[] defargs;
            Integer maxSize;
            Integer prioritisedMaxPeakListSize;
            int maxPeakListSize;
            boolean enabled;
            String queuename;
            String priouser;
            String lowPrioUser;
            String excludedUser;
            conf.recoverServers();
            
            synchronized (queue) {
                defargs = queue.Args;
                maxSize = queue.maxFastaSize;
                enabled = queue.enabled && !queue.paused.get();
                queuename = queue.name;
                prioritisedMaxPeakListSize = queue.prioritisedMaxPeakList;
                maxPeakListSize = queue.maxPeakList;
                priouser = queue.prioUser;
                lowPrioUser = queue.lowPrioUser;
                excludedUser = queue.excludedUser;
            }
            
            if (enabled) {
                if (!oldEnabled) {
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO,"queue " + queuename + " enabled");
                }
                
                String[] args = new String[defargs.length + 2];
                XiSearch nextRun = null; 
                
                if (smallSearchesRun <5) {
                    if (prioritisedMaxPeakListSize!= null && prioritisedMaxPeakListSize > 0) {
                        prioritisedMaxPeakListSize = Math.min(maxPeakListSize, prioritisedMaxPeakListSize);
                        nextRun  = XiLauncher.getNextRun(maxSize, prioritisedMaxPeakListSize, conf,priouser, lowPrioUser, excludedUser);
                        if (nextRun != null) {
                            smallSearchesRun+=1;
                        }
                    }
                } else {
                    smallSearchesRun = 0;
                }
                
                if (nextRun == null) {
                    if (maxPeakListSize>0)
                        nextRun  = XiLauncher.getNextRun(maxSize, maxPeakListSize, conf,priouser, lowPrioUser, excludedUser);
                    else
                        nextRun = XiLauncher.getNextRun(maxSize, conf, priouser, lowPrioUser, excludedUser);
                }
                
                boolean haveRun = false;
                if (nextRun != null) {
                    // ok we have a run make sure nobody else is starting it.
                    haveRun = XiLauncher.lockRun(conf, nextRun);
                }
                
                if (haveRun) {
                    // we have the run and canstart xi now
                    if (System.getProperty("TRYRUN","FALSE").toUpperCase().contentEquals("TRUE")) {
                        try {
                            // it'S a try run - so just reset the satus to queuing
                            Statement st = nextRun.connection.getConnection().createStatement();
                            st.execute("UPDATE search SET status = 'queuing' WHERE  id = " + nextRun.search +";");
                        } catch (SQLException ex) {
                            Logger.getLogger(LauncherThread.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        continue;
                    }
                    
                    Config.JarDefinition j = conf.defaultJar;
                    if (nextRun.xiVersion != null && !nextRun.xiVersion.isEmpty()) {
                        j = j.getForVersion(nextRun.xiVersion);
                        if (j.getFile() == null) {
                            try {
                                Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"XiLauncher: Requested Xi version ("+ nextRun.xiVersion+") not found for search " + nextRun.search +"");
                                if (!System.getProperty("TRYRUN","FALSE").toUpperCase().contentEquals("FALSE")) {
                                    Statement st = nextRun.connection.getConnection().createStatement();
                                    st.execute("UPDATE search SET status = 'XiLauncher: Requested Xi version("+ nextRun.xiVersion +") not found' WHERE  id = " + nextRun.search +";");
                                    st.close();
                                    nextRun.connection.getConnection().commit();
                                }
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
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO,"running: " + launcher.getCommandLine());
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
                    synchronized (queue) {
                        queue.setSearching("ID : " + nextRun.search + " NAME:" + nextRun.name, launcher);
                    }
                    if (!System.getProperty("TRYRUN","FALSE").toUpperCase().contentEquals("FALSE")) {
                        launcher.launch();
                    } else {
                        try {
                            Thread.currentThread().sleep(5l);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(LauncherThread.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    
                    synchronized(queue) {
                        queue.setSearching(null, null);
                    }
                    
                    if (plout != null)  {
                        sdate = logDate.format(c.getTime());
                        plout.standardOutput("\n"+sdate+": finished Xi on database " + nextRun.connection + " serach " + nextRun.name + "(" + nextRun.search + ")\n");
                    }
                    try {
                        Logger.getLogger(LauncherThread.class.getName()).log(Level.INFO, "Setting is_executing to false");
                        Connection con = nextRun.connection.getConnection();
                        boolean prevAuto = con.getAutoCommit();
                        con.setAutoCommit(false);
                        ResultSet rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT status, is_executing, id FROM SEARCH WHERE ID = " + nextRun.search + " FOR UPDATE");
                        if (rs.next()) {
                            if (!rs.getString(1).contentEquals("completed")) {
                                rs.updateString(1, "UNFINISHED:" + rs.getString(1));
                                rs.updateBoolean(2, false);
                                rs.updateRow();
                            }
                        }
                        rs.close();
                        con.commit();
                        con.setAutoCommit(prevAuto);
                    } catch (SQLException ex) {
                        Logger.getLogger(LauncherThread.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            } else {
                if (!oldEnabled) {
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO,"queue " + queuename + " disabled");
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
