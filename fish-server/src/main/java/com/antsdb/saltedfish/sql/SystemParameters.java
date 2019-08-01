/*-------------------------------------------------------------------------------------------------
 _______ __   _ _______ _______ ______  ______
 |_____| | \  |    |    |______ |     \ |_____]
 |     | |  \_|    |    ______| |_____/ |_____]

 Copyright (c) 2016, antsdb.com and/or its affiliates. All rights reserved. *-xguo0<@

 This program is free software: you can redistribute it and/or modify it under the terms of the
 GNU GNU Lesser General Public License, version 3, as published by the Free Software Foundation.

 You should have received a copy of the GNU Affero General Public License along with this program.
 If not, see <https://www.gnu.org/licenses/lgpl-3.0.en.html>
-------------------------------------------------------------------------------------------------*/
package com.antsdb.saltedfish.sql;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang.StringUtils;

import static com.antsdb.saltedfish.util.ParseUtil.*;
import com.antsdb.saltedfish.charset.Codecs;
import com.antsdb.saltedfish.charset.Decoder;
import com.antsdb.saltedfish.nosql.Humpback;
import com.antsdb.saltedfish.nosql.HumpbackSession;

/**
 * 
 * @author *-xguo0<@
 */
public class SystemParameters {
    private Map<String, String> params = new HashMap<>();
    private Boolean autoCommit;
    private Integer lockTimeout;
    private Boolean no_auto_value_on_zero;
    private Decoder requestDecoder;
    private Charset resultEncoder;
    private Humpback humpback;
    private boolean strict;
    
    SystemParameters(SystemParameters parent) {
        for (Map.Entry<String, String> i:parent.params.entrySet()) {
            set(i.getKey(), i.getValue());
        }
    }
    
    SystemParameters(Humpback humpback) {
        this.humpback = humpback;
        loadParams();
    }
    
    private void loadParams() {
        for (Map.Entry<String, String> i:this.humpback.getAllConfig().entrySet()) {
            this.params.put(i.getKey(), i.getValue());
        }
    }

    public String get(String key) {
        key = key.toLowerCase();
        String value = this.params.get(key);
        if (value != null) {
            return value;
        }
        return value;
    }

    public void setPermanent(HumpbackSession hsession, String key, String value) {
        if (humpback == null) {
            // only top level one can set permanently
            throw new IllegalArgumentException();
        }
        key = key.toLowerCase();
        set(key, value);
        this.humpback.setConfig(hsession, key, value);
    }
    
    public void set(String key, String value) {
        key = key.toLowerCase();
        if (key.equals("autocommit")) {
            setAutoCommit(value);
        }
        else if (key.equals("character_set_client")) {
            setCharacterSetClient(value);
        }
        else if (key.equals("character_set_results")) {
            setCharacterSetResults(value);
        }
        else if (key.equals("ddl_lock_timeout") || key.equals("innodb_lock_wait_timeout")) {
            setLockTimeout(value);
        }
        else if (key.equals("sql_mode")) {
            setSqlMode(value);
        }
        this.params.put(key, value);
    }

    private void setSqlMode(String value) {
        if (value == null) {
            this.no_auto_value_on_zero = null;
            return;
        }
        this.no_auto_value_on_zero = false;
        this.strict = false;
        if (StringUtils.isEmpty(value)) {
            return;
        }
        for (String mode : StringUtils.split(value, ',')) {
            if (mode.equals("NO_AUTO_VALUE_ON_ZERO")) {
                this.no_auto_value_on_zero = true;
            }
            else if (mode.equals("STRICT_TRANS_TABLES")) {
                this.strict = true;
            }
            else if (mode.equals("STRICT_ALL_TABLES")) {
                this.strict = true;
            }
            else {
                throw new OrcaException("unknown sql mode " + mode);
            }
        }
    }

    private void setLockTimeout(String value) {
        this.lockTimeout = Integer.parseInt(value) * 1000;
    }

    private void setCharacterSetResults(String value) {
        if (value == null) {
            this.resultEncoder = null;
            return;
        }
        String codec = value.toString();
        if (codec.equalsIgnoreCase("utf8mb4")) {
            codec = "utf8";
        }
        else if (codec.equalsIgnoreCase("binary")) {
            // internal format of strings in mysql is utf8
            codec = "utf8";
        }
        Charset cs = Charset.forName(codec);
        if (cs == null) {
            throw new OrcaException("unknown character set name", codec);
        }
        this.resultEncoder = cs;
    }

    private void setCharacterSetClient(String value) {
        if (value == null) {
            this.requestDecoder = null;
            return;
        }
        String codec = value.toString();
        Decoder decoder = Codecs.get(codec.toUpperCase());
        if (decoder == null) {
            throw new OrcaException("unknown character set name", codec);
        }
        this.requestDecoder = decoder;
    }

    private void setAutoCommit(String value) {
        if (value == null) {
            this.autoCommit = null;
            return;
        }
        if (value.equals("1")) {
            this.autoCommit = true;
        }
        else if (value.equals("0")) {
            this.autoCommit = false;
        }
        else {
            throw new OrcaException("{} is not a valid value for autocommit", value);
        }
    }
    
    /**
     * {@link} http://dev.mysql.com/doc/refman/5.7/en/sql-mode.html#sqlmode_no_auto_value_on_zero   
     * @return
     */
    public boolean isNoAutoValueOnZero() {
        return (this.no_auto_value_on_zero != null) ? this.no_auto_value_on_zero : false;
    }
    
    /**
     * get lock time out in milliseconds
     * 
     * @return
     */
    public int getLockTimeout() {
        return (this.lockTimeout != null) ? this.lockTimeout : 50 * 1000;
    }
    
    public boolean getAutoCommit() {
        return (this.autoCommit != null) ? this.autoCommit : true;
    }

    /**
     * decoder to convert messages sent from mysql client, equivalent to character_set_client
     * 
     * @return
     */
    public Decoder getRequestDecoder() {
        return (this.requestDecoder != null) ? this.requestDecoder : Codecs.get("UTF-8");
    }
    
    /**
     * encoder to convert literal result to the client, equivalent to character_set_results 
     * @return
     */
    public Charset getResultEncoder() {
        return (this.resultEncoder != null) ? this.resultEncoder : Charsets.UTF_8;
    }
    
    public String getDatabaseType() {
        String type = get("databaseType");
        return (type != null) ? type : "MYSQL";
    }
    
    public int getSlaveServerId() {
        int value = parseInteger(get("antsdb_mysqlslave_slave_server_id"));
        return value;
    }

    public String getSlaveUser() {
        return get("antsdb_mysqlslave_master_user");
    }

    public String getSlavePassword() {
        return get("antsdb_mysqlslave_master_password");
    }
    
    public String getMasterHost() {
        return get("antsdb_mysqlslave_master_host");
    }

    public int getMasterPort() {
        return parseInteger("antsdb_mysqlslave_master_port", 3306);    
    }
    
    public Set<String> getIgnoreList() {
        String value = get("antsdb_mysqlslave_slave_ignore_db");
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String i:StringUtils.split(value, ",")) {
            result.add(i);
        }
        return result;
    }
    
    public boolean isAsynchronousImportEnabled() {
        return parseBoolean(get("antsdb_asynchronous_import"), true);
    }
    
    public int getAsyncInsertThreads() {
        if (get("antsdb_asynchronous_import_threads") == null) {
            int result = Runtime.getRuntime().availableProcessors();
            if (result > 8) {
                result = 8;
            }
            return result;
        }
        else {
            return parseInteger(get("antsdb_asynchronous_import_threads"), 8);
        }
    }
    
    public boolean getForeignKeyCheck() {
        String result = get("foreign_key_checks");
        return (result == null) ? true : "1".equals(result); 
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(this.params);
    }

    public String getAuthPlugin() {
        return get("antsdb_auth_plugin");
    }

    public byte[] getSeed() {
        String seed = get("antsdb_auth_seed");
        return seed.getBytes();
    }
    
    public boolean isStrict() {
        return this.strict;
    }
}
