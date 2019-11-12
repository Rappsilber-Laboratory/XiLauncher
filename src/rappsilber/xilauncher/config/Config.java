/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 *
 * @author lfischer
 */
public class Config {

    public class JarDefinition {
        static final String DEFAULT_EXTENSION  = ".jar";
        String baseName;
        String extension;
        String Version;
        String path;

        public JarDefinition(String path, String baseName) {
            this(path,baseName, DEFAULT_EXTENSION, null);
        }

        public JarDefinition(String path, String baseName, String extension) {
            this(path, baseName, extension, null);
        }

        public JarDefinition(String path, String baseName, String extension, String Version) {
            if (path.endsWith(File.separator))
                path=path.substring(0,path.length()-1);
            this.path = path;
            this.baseName = baseName;
            if (extension.length()>0 && !extension.startsWith("."))
                this.extension = "." + extension;
            else
                this.extension = extension;
            this.Version = Version;
        }

        /**
         * turn the information into a absolute path
         * @return 
         */
        public String getFile() {
            String concPath = path + File.separator + baseName + (Version == null ? "":Version) + extension;
            File f = new File(concPath);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
            if (!concPath.startsWith(File.separator)) {
                f = new File(File.separator+concPath);
                if (f.exists()) {
                    return f.getAbsolutePath();
                }
            }

            concPath = path + File.separator + baseName +"_" + (Version == null ? "":Version) + extension;
            f = new File(concPath);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
            if (!concPath.startsWith(File.separator)) {
                f = new File(File.separator+concPath);
                if (f.exists()) {
                    return f.getAbsolutePath();
                }
            }

            return null;
        }



        /**
         * turn the information into a absolute path
         * @return 
         */
        public String getFile(ArrayList<String> basePath) {
            String concPath = path + File.separator + baseName + (Version == null ? "":Version) + extension;
            File f = new File(concPath);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
            if (concPath.startsWith(File.separator))
                concPath=concPath.substring(1);

            for (String base : basePath) {
                f = new File(base+File.separator+concPath);
                if (f.exists()) {
                    return f.getAbsolutePath();
                }

            }
            concPath = path + File.separator + baseName +"_"+ (Version == null ? "":Version) + extension;
            f = new File(concPath);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
            if (concPath.startsWith(File.separator))
                concPath=concPath.substring(1);

            for (String base : basePath) {
                f = new File(base+File.separator+concPath);
                if (f.exists()) {
                    return f.getAbsolutePath();
                }

            }

            return null;
        }


        /**
         * turn the information into a absolute path
         * @return 
         */
        public String getFileRelative(ArrayList<File> basePath) {
            ArrayList<String> forward  = new ArrayList<String>(basePath.size());
            for (File f: basePath) {
                if (f.isFile()) {
                    f=f.getParentFile();
                }
                forward.add(f.getAbsolutePath());
            }
            return getFile(forward);
        }

        public JarDefinition getForVersion(String version) {
            return new JarDefinition(path, baseName, extension, version);
        }


    }
    
    public class DBConnection {
        

        String server = null;
        Integer port = null;
        String db;
        String user = "";
        String password = "";
        Connection con = null;
        String base_path = null;
        String conString = null;
        boolean connection_opened = false;
        int retry = 10;
        long disabledSince = 0;

        public String getConnectionString() {
            if (conString == null) {
                if (db == null) {
                    return null;
                }
                if ((server == null || server.isEmpty()) && port == null) {
                    conString = "jdbc:postgresql:" + db;
                } else if (server == null || server.isEmpty()) {
                    conString = "jdbc:postgresql://127.0.0.1:" + port + "/" + db;
                } else if (port == null) {
                    conString = "jdbc:postgresql://" + server + ":5432/" + db;
                } else {
                    conString = "jdbc:postgresql://" + server + ":" + port + "/" + db;
                }
            }
            return conString;
        }

        public String getUser() {
            return user;
        }
        
        public String getPassword() {
            return password;
        }
        
        public synchronized Connection getConnection() throws SQLException {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            
            if (!connection_opened || !testConnection(con)) {
                connection_opened = false;
                String conString = getConnectionString();
                if (conString == null) {
                    return null;
                }
                try {
                    Class.forName("org.postgresql.Driver");
                    con = DriverManager.getConnection(conString, user, password);
                    connection_opened = true;
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                    throw new SQLException(ex);
                }

            }

            return con;
        }

        public DBConnection(String server, Integer port, String db) {
            this(server, db);
            this.port = port;
        }

        public DBConnection(String server, String db) {
            this(db);
            this.server = server;
        }

        public DBConnection(String db) {
            if (db.startsWith("jdbc:")) {
                conString = db;
                if (db.contains("/")) {
                    this.db = db.substring(db.lastIndexOf("/") + 1);
                } else {
                    this.db = db.substring(db.lastIndexOf(":") + 1);
                }
            } else {
                this.db = db;
            }

        }

        public DBConnection(String db, String user, String password) {
            if (db.startsWith("jdbc:")) {
                conString = db;
                if (db.contains("/")) {
                    this.db = db.substring(db.lastIndexOf("/") + 1);
                } else {
                    this.db = db.substring(db.lastIndexOf(":") + 1);
                }
            } else {
                this.db = db;
            }
            this.user = user;
            this.password = password;

        }

        public DBConnection(String server, Integer port, String db, String user) {
            this(server, port, db);
            this.user = user;
        }

        public DBConnection(String server, Integer port, String db, String user, String password) {
            this(server, port, db, user);
            this.password = password;
        }

        public String logPrefix() {
            if ((server == null || server.isEmpty())) {
                return db;
            } else {
                return server + "_" + db;
            }
        }

        public String getBasePath() {
            if (base_path != null) {
                return base_path;
            }
            try {
                Statement st = getConnection().createStatement();
                ResultSet rs = st.executeQuery("SELECT setting from base_setting WHERE name = 'base_directory_path';");
                if (rs.next()) {
                    base_path = rs.getString(1);
                }
                rs.close();
                st.close();
                return base_path;
            } catch (SQLException se) {
                return base_path;
            }

        }

        public boolean decreaseRetry() {
            return (--retry) == 0;
        }

        public void resetRetry() {
            retry = 10;
        }

        private boolean testConnection(Connection con) {
            try {
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1;");
                st.close();
                return true;
            } catch (SQLException ex) {
                Logger.getLogger(Config.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    con.close();
                } catch (Exception e) {}
            }
            return false;
        }

    }

    public class Queue {

        public String name = null;
        public Integer maxFastaSize = null;
        public int prioritisedMaxPeakList = 0;
        public int maxPeakList = 0;
        public String prioUser = null;
        public String lowPrioUser = null;
        public String[] Args = null;
        public boolean enabled = true;
        public boolean stop = false;
        public boolean started = false;
    }

//    public int[] maxFastaSize;
//    public String[][] XiArgs;
    public Queue[] queues = new Queue[0];
    public HashMap<String, String> replacements = new HashMap<String, String>();

    public ArrayList<DBConnection> server = new ArrayList<DBConnection>();
    public ArrayList<DBConnection> disabledServer = new ArrayList<DBConnection>();

    public String basePath;

    File configFile;
    HashMap<File, Long> configFileChangeTime = new HashMap<File, Long>();

    Object sync = new Object();
    public boolean stop = false;

    public long connectionRecoveryTryMili = 1000 * 60 * 30;
    
    public JarDefinition defaultJar = new JarDefinition(".", "XiSearch");

//    public Config() throws IOException {
//        readDefaultConfig();
//    }
    public Config(String configFile) throws FileNotFoundException, IOException {
        this(new File(configFile));
    }

    public Config(File configFile) throws FileNotFoundException, IOException {
        readConfig(configFile);
    }



    public void readConfig(File fconfig) throws FileNotFoundException, IOException {
        this.configFile = fconfig;
        configFileChangeTime.put(fconfig, configFile.lastModified());
        
        ArrayList<Queue> confqueues = new ArrayList<Queue>(queues.length);
        ArrayList<File> configFiles = new ArrayList<File>(1);
        configFiles.add(configFile);
        replacements = new HashMap<String, String>();
        boolean pause = false;
        
        synchronized (sync) {
            for (int c = 0; c < configFiles.size(); c++) {
                File cf = configFiles.get(c);
                BufferedReader config = new BufferedReader(new FileReader(cf));
                
//                ArrayList<Integer> queueSizes = new ArrayList<Integer>();
//                ArrayList<String[]> queueArguments = new ArrayList<String[]>();
                int ln = 1;
                String line = config.readLine();

                while (line != null) {
                    if (line.trim().toLowerCase().contentEquals("[pause]")) {
                        pause = true;
                        line = config.readLine();
                    } else if (line.trim().toLowerCase().contentEquals("[pause]")) {
                        pause = true;
                        Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"Config file contains [PAUSE]: pausing all threads");
                        line = config.readLine();
                    } else if (line.trim().toLowerCase().contentEquals("[stop]")) {
                        stop = true;
                        Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"Config file contains [STOP]: closing down launcher");
                        line = config.readLine();
                    } else if (line.trim().toLowerCase().contentEquals("[replacements]")) {
//                            int sectionLine = ln;
                        line = config.readLine();
                        ln++;
                        while (line != null && !line.trim().startsWith("[")) {
                            if (!line.trim().startsWith("#") && line.contains("=")) {
                                String[] pair = line.split("=", 2);
                                replacements.put("%" + pair[0] + "%", pair[1]);
                            }
                            line = config.readLine();
                        }
                    } else if (line.trim().toLowerCase().contentEquals("[include]")) {
//                            int sectionLine = ln;
                        line = config.readLine();
                        ln++;
                        while (line != null && !line.trim().startsWith("[")) {
                            if (!line.trim().startsWith("#") && !line.trim().isEmpty()) {
                                File included = new File(line.trim());
                                if (!included.exists()) {
                                    File currentFile = configFiles.get(c);
                                    included = new File(currentFile.getParent() + File.separator + line.trim());
                                }
                                configFiles.add(included);
                                configFileChangeTime.put(included, included.lastModified());
                            }
                            line = config.readLine();
                        }
                    } else if (line.trim().toLowerCase().contentEquals("[queue]")) {
                        Queue q = new Queue();
                        confqueues.add(q);
                        int sectionLine = ln;
                        line = config.readLine();
                        ln++;
                        while (line != null && !line.trim().startsWith("[")) {
                            String tline = line.trim();
                            if (!(tline.isEmpty() || tline.startsWith("#"))) {
                                String[] sl = tline.split("=", 2);
                                if (sl[0].trim().toLowerCase().contentEquals("queue")) {
                                    q.name = sl[1];
                                } else if (sl[0].trim().toLowerCase().contentEquals("priorityuser")) {
                                    q.prioUser = sl[1].trim();
                                } else if (sl[0].trim().toLowerCase().contentEquals("lowpriorityuser")) {
                                    q.lowPrioUser = sl[1].trim();
                                } else if (sl[0].trim().toLowerCase().contentEquals("maxfastasize")) {
                                    String ssize = sl[1].trim().toUpperCase();
                                    q.maxFastaSize = parseSize(ssize);
                                } else if (sl[0].trim().toLowerCase().contentEquals("prioritisedpeaklistsize")) {
                                    String ssize = sl[1].trim().toUpperCase();
                                    
                                    q.prioritisedMaxPeakList = parseSize(ssize);
                                } else if (sl[0].trim().toLowerCase().contentEquals("maxpeaklistsize")) {
                                    String ssize = sl[1].trim().toUpperCase();
                                    q.maxPeakList = parseSize(ssize);
                                } else if (sl[0].trim().toLowerCase().contentEquals("arguments")) {
                                    String[] ARGUMENTS = sl[1].trim().split("\\s+");
                                    boolean dbFound = false;
                                    for (int a = 0; a < ARGUMENTS.length; a++) {
                                        if (ARGUMENTS[a].contains("%DB%")) {
                                            dbFound = true;
                                        }
                                    }
                                    if (!dbFound) {
                                        Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"ARGUMENTS don't provide a db-placeholder (%DB%) (line:" + ln + "):" + line + "\n -> queue disabled");
                                        q.enabled = false;
                                    }
                                    q.Args = ARGUMENTS;
                                } else if (sl[0].trim().toLowerCase().contentEquals("enabled") && q.enabled) {
                                    String en = sl[1].trim().toLowerCase();
                                    if (sl[1].trim().toLowerCase().matches("(1|yes|y|true|t|ja|j)")) {
                                        q.enabled = true;
                                    } else if (sl[1].trim().toLowerCase().matches("(0|no|n|false|f|nein)")) {
                                        q.enabled = false;
                                    } else {
                                        Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"unknow argument for enabled:(line " + ln + "):" + line + "\n -> queue disabled");
                                        q.enabled = false;
                                    }

                                } else {
                                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"Unknown option in section [queue] (line:" + ln + "):" + line + "\n -> queue disabled");
                                    q.enabled = false;
                                }
                            }
                            line = config.readLine();
                            ln++;
                        }
                        if (q.maxFastaSize == null) {
                            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"[QUEUE]-section without size information(MAXFASTASIZE=XXX) (line:" + sectionLine + ")");
                            System.exit(-1);
                        }
                        if (q.Args == null) {
                            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"[QUEUE]-section without arguments (ARGUMENTS=XXX) (line:" + sectionLine + ")");
                            System.exit(-1);
                        }
                    } else if(line.trim().toLowerCase().contentEquals("[jar]")) {
                        String base="xiVersions";
                        String ext =".jar";
                        String version = null;
                        String path = ".";
                        String b = "base=";
                        String e ="extension=";
                        String v = "version=";
                        String p = "path=";
                        line = config.readLine();
                        ln++;
                        while (line != null && !line.trim().startsWith("[")) {
                            if (!(line.trim().startsWith("#")|| line.trim().isEmpty())) {
                                if (line.startsWith(b)) {
                                    base = line.substring(b.length()).trim();
                                } else if (line.startsWith(e)) {
                                    ext = line.substring(e.length()).trim();
                                } else if (line.startsWith(v)) {
                                    version = line.substring(v.length()).trim();
                                } else if (line.startsWith(p)) {
                                    path = line.substring(p.length()).trim();
                                } else {
                                    Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"Unknown option in section [JAR] (line:" + ln + "):" + line + "\n -> line ignored");
                                }
                            }

                            line = config.readLine();
                            ln++;
                        }
                        defaultJar = new JarDefinition(path, base, ext, version);
                        if (defaultJar.getFile() == null) {
                            defaultJar = new JarDefinition(cf.getAbsoluteFile().getParent()+File.separator+path, base, ext, version);
                        }
                        if (defaultJar.getFile() == null) {
                            defaultJar = new JarDefinition(configFile.getAbsoluteFile().getParent()+File.separator+path, base, ext, version);
                        }
                        if (defaultJar.getFile() == null) {
                            defaultJar = new JarDefinition(path, base, ext, version);
                        }
                        
                    } else if (line.trim().toLowerCase().contentEquals("[database]")) {
                        String connection = null;
                        String user = "user";
                        String password = null;
                        int sectionLine = ln;
                        line = config.readLine();
                        ln++;
                        while (line != null && !line.trim().startsWith("[")) {
                            String tline = line.trim();
                            if (!(tline.isEmpty() || tline.startsWith("#"))) {
                                String[] sl = tline.split("=", 2);
                                if (sl[0].trim().toLowerCase().contentEquals("connection")) {
                                    connection = sl[1].trim();
                                } else if (sl[0].trim().toLowerCase().contentEquals("user")) {
                                    user = sl[1].trim();
                                } else if (sl[0].trim().toLowerCase().contentEquals("password")) {
                                    password = sl[1].trim();
                                } else {
                                    Logger.getLogger(this.getClass().getName()).log(Level.WARNING,"Unknown option in section [database] (line:" + ln + "):" + line + "\n -> ignored");
                                }
                            }
                            line = config.readLine();
                            ln++;
                        }
                        if (connection == null) {
                            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE,"[DATABASE]-section connection SETTING MISSING (CONNECTION=jdb:postgrsql://server:port/db)");
                        } else {
                            try {
                                if (user != null) {
                                    if (password == null) {
                                        password = "";
                                    }
                                    server.add(new DBConnection(connection, user, password));
                                } else {
                                    server.add(new DBConnection(connection, user, password));
                                }
                            } catch (Exception e) {

                            }
                        }
                    } else {
                        line = config.readLine();
                        ln++;
                    }
                }
            }

            this.queues = new Queue[confqueues.size()];
            confqueues.toArray(this.queues);

            //apply replacements t replacements
            boolean replaced = false;
            int round = 0;
            do {
                replaced = false;
                for (Map.Entry<String, String> r : replacements.entrySet()) {
                    for (Map.Entry<String, String> e : replacements.entrySet()) {
                        if (r.getValue().contains(e.getKey())) {
                            r.setValue(r.getValue().replace(e.getKey(), e.getValue()));
                        }
                    }
                }
            } while (replaced && round++ < 5);
            if (replaced) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Looks like there is some cyclic replacement definition");
                System.exit(-1);
            }

            //apply replacements
            for (Queue q : queues) {
                for (Map.Entry<String, String> e : replacements.entrySet()) {
                    for (int a = 0; a < q.Args.length; a++) {
                        q.Args[a] = q.Args[a].replace(e.getKey(), e.getValue());
                    }
                }
            }

            if (stop == true) {
                for (Queue q : queues) {
                    q.stop = true;
                }
            }

            if (pause == true) {
                for (Queue q : queues) {
                    q.enabled = false;
                }
            }

//            this.queus = XiArgs.length;
        }

        Logger.getLogger(this.getClass().getName()).log(Level.INFO,"Queues:" + queues.length);

    }

    public int parseSize(String ssize) throws NumberFormatException {
        double m = 1;
        double v = 0;
        if (ssize.endsWith("G")) {
            m = 1024 * 1024 * 1024;
            ssize = ssize.substring(0, ssize.length() - 1).trim();
        } else if (ssize.endsWith("M")) {
            m = 1024 * 1024;
            ssize = ssize.substring(0, ssize.length() - 1).trim();
        } else if (ssize.endsWith("K")) {
            m = 1024;
            ssize = ssize.substring(0, ssize.length() - 1).trim();
        }
        int totalSize = (int) (Double.parseDouble(ssize) * m);
        return totalSize;
    }

    public boolean configChanged() {
        if (configFile == null) {
            return false;
        }
        for (Map.Entry<File, Long> e : configFileChangeTime.entrySet()) {
            if (e.getKey().lastModified() != e.getValue()) {
                return true;
            }
        }

        return false;
    }

    public void reReadConfig() throws FileNotFoundException, IOException {
        Config nc = new Config(configFile);
        this.configFileChangeTime = nc.configFileChangeTime;
        ArrayList<Queue> newThreads = new ArrayList<Queue>();
        ArrayList<Queue> foundThreads = new ArrayList<Queue>();

        for (Queue q : nc.queues) {
            boolean qfound = false;
            for (Queue qo : queues) {
                if (q.name.contentEquals(qo.name)) {
                    synchronized (qo) {
                        qo.Args = q.Args;
                        qo.enabled = q.enabled;
                        qo.stop = q.stop;
                        qo.maxFastaSize = q.maxFastaSize;
                        qo.prioUser = q.prioUser;
                        qo.lowPrioUser = q.lowPrioUser;
                        qo.prioritisedMaxPeakList = q.prioritisedMaxPeakList;
                        qo.maxPeakList = q.maxPeakList;
                    }
                    qfound = true;
                    foundThreads.add(q);
                }
            }
            if (!qfound) {
                newThreads.add(q);
            }
        }

        // find obsolete queues
        for (Queue qo : queues) {
            boolean qfound = false;
            for (Queue q : nc.queues) {
                if (qo.name.contentEquals(q.name)) {
                    qfound = true;
                }
            }
            if (!qfound) {
                qo.stop = true;
            }
        }

        if (newThreads.size() > 0) {
            Queue[] qs = java.util.Arrays.copyOf(queues, queues.length + newThreads.size());
            for (int q = queues.length; q < qs.length; q++) {
                qs[q] = newThreads.get(q - queues.length);
            }
            queues = qs;
        }
        this.server = nc.server;
        this.defaultJar = nc.defaultJar;
    }

    public void disableServer(DBConnection server) {
        synchronized (this.disabledServer) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Disabling a server:" + server.conString);
            this.server.remove(server);
            server.disabledSince = Calendar.getInstance().getTimeInMillis();
            this.disabledServer.add(server);
            try {
                Thread.sleep(2);
            } catch (InterruptedException ex) {
                Logger.getLogger(Config.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void recoverServers() {
        long current = Calendar.getInstance().getTimeInMillis();
        long recovertime = current - connectionRecoveryTryMili;
        ArrayList<DBConnection> recovered = new ArrayList<DBConnection>();
        synchronized (disabledServer) {
            for (DBConnection s : disabledServer) {
                if (s.disabledSince < recovertime) {
                    DBConnection toRecover = s;
                    try {
                        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Try to reconnect to server:" + toRecover.conString);
                        if (toRecover.getConnection() != null) {
                            toRecover.resetRetry();
                            server.add(toRecover);
                            recovered.add(s);
                            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Reconnected to server:" + toRecover.conString);
                        } else {
                            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Failed to reconnect to server:" + toRecover.conString);
                            toRecover.disabledSince = current;
                        }
                    } catch (SQLException ex) {
                        //Logger.getLogger(Config.class.getName()).log(Level.SEVERE, null, ex);
                        Logger.getLogger(this.getClass().getName()).log(Level.INFO, "Failed to reconnect to server:" + toRecover.conString);
                        toRecover.disabledSince = current;
                    }

                }
            }
            for (DBConnection k : recovered) {
                disabledServer.remove(k);
            }
        }
    }
    
    

}
