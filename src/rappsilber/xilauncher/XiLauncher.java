/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rappsilber.utils.UpdateableInteger;
import rappsilber.xilauncher.config.Config;

/**
 *
 * @author lfischer
 */
public class XiLauncher {
    
    private static boolean stop = false;
    
    
    protected static LauncherThread[] startThreads(Config conf, String logDir) {
        LauncherThread[] threads = new LauncherThread[conf.queues.length];
        //        XiLauncher[] launcher;
        //        launcher = new XiLauncher[q];
        
        for (int i = 0; i < conf.queues.length; i++) {
            LauncherThread launcher = new LauncherThread(conf.queues[i], conf, logDir);
            threads[i] = launcher;
            launcher.start();
        }
        return threads;
    }
 
    public static int getDatabaseSize(Connection con, int search, String basePath) {
        int dbSize = 0;
        File fbp = new File (basePath);
        
        if (!fbp.exists()) {
            basePath = basePath.replace("/", File.separator).replace("\\", File.separator);
            fbp = new File (basePath);
        }
            
        // get the assigned fasta-files
        try {
            ResultSet rs = con.createStatement().executeQuery(
                    "SELECT "
                    + "sf.file_name, "
                    + "sf.file_path "
                    + "FROM "
                    + "sequence_file sf INNER JOIN "
                    + "search_sequencedb ss ON sf.id = ss.seqdb_id INNER JOIN "
                    + "search s ON ss.search_id = s.id "
                    + "WHERE s.id = " + search);
            
            while (rs.next())  {
                File path = new File(fbp.getAbsolutePath() + File.separator + rs.getString(2));
                if (path.exists()) {
                    if (path.isFile()) {
                        dbSize += path.length();
                    } else {
                        path = new File(path.getAbsolutePath() + File.separator + rs.getString(1));
                        if (path.exists() && path.isFile()) {
                            dbSize += path.length();
                        } else
                            path = null;
                    }
                }
                // try without the base-path
                if (path == null) {
                    path = new File(rs.getString(2));
                    if (path.exists()) {
                        if (path.isFile()) {
                            dbSize += path.length();
                        } else {
                            path = new File(path.getAbsolutePath() + File.pathSeparator + rs.getString(1));
                            if (path.exists() && path.isFile()) {
                                dbSize += path.length();
                            } else
                                path = null;
                        }
                    }
                    
                }
                if (path == null)
                    return -1;
            }
            
        } catch (SQLException se) {
            return -1;
        }
        return dbSize;
    }
    
    public static int getPeakListSize(Connection con, int search, String basePath) {
        int dbSize = 0;
        File fbp = new File (basePath);
        
        if (!fbp.exists()) {
            basePath = basePath.replace("/", File.separator).replace("\\", File.separator);
            fbp = new File (basePath);
        }
            
        // get the assigned fasta-files
        try {
            ResultSet rs = con.createStatement().executeQuery(
                    "SELECT "
                    + "r.name, "
                    + "r.file_path "
                    + "FROM "
                    + "run r INNER JOIN "
                    + "search_acquisition sa ON r.run_id = sa.run_id AND r.acq_id = sa.acq_id INNER JOIN "
                    + "search s ON sa.search_id = s.id "
                    + "WHERE s.id = " + search);
            
            while (rs.next())  {
                File path = new File(fbp.getAbsolutePath() + File.separator + rs.getString(2));
                if (path.exists()) {
                    if (path.isFile()) {
                        dbSize += path.length();
                    } else {
                        path = new File(path.getAbsolutePath() + File.separator + rs.getString(1));
                        if (path.exists() && path.isFile()) {
                            dbSize += path.length();
                        } else
                            path = null;
                    }
                }
                // try without the base-path
                if (path == null) {
                    path = new File(rs.getString(2));
                    if (path.exists()) {
                        if (path.isFile()) {
                            dbSize += path.length();
                        } else {
                            path = new File(path.getAbsolutePath() + File.pathSeparator + rs.getString(1));
                            if (path.exists() && path.isFile()) {
                                dbSize += path.length();
                            } else
                                path = null;
                        }
                    }
                    
                }
                if (path == null)
                    return -1;
            }
            
        } catch (SQLException se) {
            return -1;
        }
        return dbSize;
    }
    
    
    private static HashMap<String,Calendar> runs = new HashMap<String,Calendar>();
    
    private static HashMap<Config.DBConnection,LinkedList<Integer>>  usercount = new HashMap<Config.DBConnection, LinkedList<Integer>>();

    public static synchronized XiSearch getNextRun(int maxFastaSize, Config conf, String prioUser){
        return getNextRun(maxFastaSize, 0, conf,prioUser);
    }
    
    /**
     * Will change the status of the search to selected in an atomic way.
     * Returns whether it could change the status and therefor lock the search 
     * as to be searched by this instance of the launcher.<br>
     * If two launchers are running (e.g. using to servers) then there could be 
     * a chance that both try to start the same search at same time and then
     * we would end up with having each result twice.<br>
     * This locking prevents this from happening.
     * @param conf
     * @param connection
     * @param search
     * @param name
     * @return 
     */
    public static boolean lockRun(Config conf, XiSearch run) { 
        Connection con = null;
        boolean canStart = false;
        
        for (Config.DBConnection db: conf.server) {
            // get all searches, that are queuing and not executing
            if (db.getConnectionString().contentEquals(run.connection.getConnectionString())) {
                try {
                    con = db.getConnection();
                    db.resetRetry();
                } catch (Exception e)  {
                    System.err.println("Error getting database connection");
                    System.err.println(e);
                    if (db.decreaseRetry()) {
                        conf.disableServer(db);
                        return false;
                    }
                }
                break;
            }
        }
            
        try {
            con.setAutoCommit(false);
            ResultSet rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT status, id FROM SEARCH WHERE ID = " + run.search + " AND status = 'queuing' AND NOT is_executing FOR UPDATE");
            if (rs.next()) {
                rs.updateString(1, "SELECTED");
                rs.updateRow();
                canStart=true;
            }
            rs.close();
            con.commit();
            if (canStart) {
                boolean versionSet = false;
                rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT customsettings FROM parameter_set ps INNER JOIN search s on s.paramset_id = ps.id WHERE s.id = " + run.search +";");
                if (rs.next()) {
                    String xiv = "xiversion:";
                    String custom = rs.getString(1);
                    String[] lines = custom.split("[\n\r]+");
                    for (String l : lines) {
                        if (l.trim().toLowerCase().startsWith(xiv)) {
                            run.xiVersion = l.trim().substring(xiv.length()).trim();
                            versionSet = true;
                        }
                    }
                }
                rs.close();
                try {
                    if (!versionSet) {
                        // do we have thetable xi-versions?
                        rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT EXISTS (\n" +
                                "   SELECT 1\n" +
                                "   FROM   information_schema.tables \n" +
                                "   WHERE  table_schema = 'public'\n" +
                                "   AND    table_name = 'xiversions'\n" +
                                "   );");

                        rs.next();
                        boolean canSetVersion = rs.getBoolean(1);
                        rs.close();
                        
                        if (canSetVersion) {
                            rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT version FROM search s inner join xiversions xv on s.xiversion = xv.id WHERE s.id = " + run.search +";");
                            if (rs.next()) {
                                String version = rs.getString(1);
                                if ( version != null ) {
                                    run.xiVersion = version.trim();
                                    versionSet  = true;
                                }
                            }
                            rs.close();
                            if (!versionSet) {
                                rs = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT).executeQuery("SELECT version FROM xiversions xv WHERE isdefault;");
                                if (rs.next()) {
                                    String version = rs.getString(1);
                                    if ( version != null ) {
                                        run.xiVersion = version.trim();
                                    }
                                }
                                rs.close();
                            }
                        }
                    }
                } catch (SQLException se) {
                    Logger.getLogger(XiLauncher.class.getName()).log(Level.WARNING,"could not read xi-versions from the database",se);
                }
                
            }
            return canStart;
                    
        } catch (SQLException ex) {
            Logger.getLogger(XiLauncher.class.getName()).log(Level.SEVERE, null, ex);
            try {
                con.rollback();
            } catch (SQLException ex1) {
                Logger.getLogger(XiLauncher.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
        return false;
        
    }
    
    
    public static synchronized XiSearch getNextRun(int maxFastaSize, int maxPeakListSize, Config conf, String prioUser){
        Calendar now = Calendar.getInstance();
        HashSet<String> cleanup = new HashSet<String>();
        for (String run : runs.keySet()) {
            if (now.getTimeInMillis() - runs.get(run).getTimeInMillis() > 60*1000) {
                cleanup.add(run);
            }
        }
        
        for (String run : cleanup) {
            runs.remove(run);
        }
        
        for (Config.DBConnection db: conf.server) {
            // get all searches, that are queuing and not executing
            Connection con = null;
            try {
                con = db.getConnection();
            } catch (Exception e)  {
                System.err.println("Error getting database connection");
                System.err.println(e);
                if (db.decreaseRetry()) {
                    conf.disableServer(db);
                    return null;
                }
            }

            // prevent a single user to block all queues indefinetly we count how much we searched for him/her
            LinkedList<Integer> dbUserCount = usercount.get(db);
            if (dbUserCount == null) {
                dbUserCount = new LinkedList<Integer>();
                usercount.put(db, dbUserCount);
            }
            HashMap<Integer,UpdateableInteger> counts = new HashMap<Integer, UpdateableInteger>();
            for (Integer u : dbUserCount) {
                UpdateableInteger i = counts.get(u);
                if (i == null) {
                    i= new UpdateableInteger(1);
                    counts.put(u, i);
                } else {
                    i.value++;
                }
            }
            StringBuilder orderby = new StringBuilder();
            for (Map.Entry<Integer, UpdateableInteger> e : counts.entrySet()) {
                if (e.getValue().value > 2) {
                    orderby.append(" WHEN uploadedby = ").append(e.getKey()).append(" THEN ").append(e.getValue().value);
                }
            }
            if (orderby.length() >0) {
                orderby.insert(0, "ORDER BY CASE ");
                orderby.append(" ELSE 0 END, ID ");
            } else {
                orderby.append("ORDER BY ID");
            }
            if (prioUser != null) {
                if (prioUser.matches("[0-9]+")) {
                    orderby.insert(8, " CASE WHEN uploadedby = " + prioUser +" THEN 0 ELSE 1 END, ");
                } else {
                    orderby.insert(8, " CASE WHEN uploadedby = (select id from users where user_name = '" + prioUser +"') THEN 0 ELSE 1 END, ");
                }
            }
            
            
            try {
                if (con != null) {
                    ResultSet rs = con.createStatement().executeQuery("SELECT id, name, uploadedby FROM search WHERE status='queuing' and is_executing = 'false' and (hidden is null OR hidden = false) " + orderby + ";");
                    while (rs.next()) {
                        int searchid = rs.getInt(1);
                        String basepath = db.getBasePath();
                        int s = getDatabaseSize(con, searchid, basepath);
                        int p = getPeakListSize(con, searchid, basepath);
                        if (s<= maxFastaSize && (maxPeakListSize <=0 || (p>0 && maxPeakListSize>=p))) { 
                            XiSearch ret = new XiSearch();
                            ret.connection=db;
                            ret.search=searchid;
                            ret.name=rs.getString(2);
                            String run = ret.connection.getConnectionString() + ret.search;
                            // was not run in a bit
                            if (!runs.containsKey(run)) {
                                runs.put(run, Calendar.getInstance());
                                // add the uploader to the list
                                dbUserCount.add(rs.getInt(3));
                                // delete first entry
                                while (dbUserCount.size() > 20) {
                                    dbUserCount.remove(0);
                                }

                                return ret;
                            }
                        }
                    }
                }
            } catch (SQLException se) {
                Logger.getLogger(XiLauncher.class.getName()).log(Level.SEVERE, null, se);
            }
        }
        return null;
        
    }
    
    
    /**
     * waits until all threads have stopped or wait milliseconds per thread have passed
     * @param threads
     * @param wait
     * @return whether all threads have stopped
     */
    protected static boolean waitForThreads(Thread[] threads, int wait) {
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join(wait);
            } catch (InterruptedException ie) {
            }
        }
        for (int i = 0; i < threads.length; i++) {
            if (threads[i].isAlive()) {
                return false;
            }
        }        
        return true;
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        Config conf = null;
        String logDir = "";
        if (args.length > 0) {
            conf = new Config(args[0]);
        } else {
            System.err.println("No configuration given");
            System.exit(-1);
        }
            
        

        stop = conf.stop;
        
        if (args.length > 1) {
            File ld = new File(args[1]);
            if (new File(args[1]).isDirectory()) {
                logDir = ld.getAbsolutePath() + File.separator;
            }
        }
        LauncherThread[] threads;
        
        // TODO code application logic here
        int q = conf.queues.length;
        
        threads = startThreads(conf, logDir);
        
        while (!waitForThreads(threads, 3000)) {
            
            if (conf.configChanged()) {
                stop = true;
                System.err.println("!!!!Config changed!!!!");
//                while (!waitForThreads(threads, 1000)) {
//                    System.err.println("waiting for searches to finish");
//                }
                System.err.println("reread config");
                conf.reReadConfig();
                stop = conf.stop;
                ArrayList<LauncherThread> newthreads = new ArrayList<LauncherThread>();
                if (!stop) {
                    for (int i = 0; i<conf.queues.length; i++) {
                        if (conf.queues[i].stop == false && conf.queues[i].started == false) {
                            newthreads.add(new LauncherThread(conf.queues[i], conf, logDir));
                        }
                    }
                    if (newthreads.size()>0) {
                        System.err.println("starting threads");
                        LauncherThread[] nt = java.util.Arrays.copyOf(threads, threads.length+newthreads.size());
                        for (int i = threads.length; i< nt.length; q++) {
                            nt[i] = newthreads.get(i-threads.length);
                            nt[i].start();
                        }
                        threads = nt;
                        
                    }
                } else {
                    System.out.println("Waiting for threads to finish.");
                }
            }
            
        }

        
    }
    
    
}
