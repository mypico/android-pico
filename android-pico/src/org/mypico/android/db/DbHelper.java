/*
 * (C) Copyright Cambridge Authentication Ltd, 2017
 *
 * This file is part of android-pico.
 *
 * android-pico is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * android-pico is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with android-pico. If not, see
 * <http://www.gnu.org/licenses/>.
 */


package org.mypico.android.db;

import java.io.File;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mypico.android.core.PicoApplication;
import org.mypico.jpico.data.pairing.KeyPairingAccessor;
import org.mypico.jpico.data.pairing.LensPairingAccessor;
import org.mypico.jpico.db.DbKeyPairingAccessor;
import org.mypico.jpico.db.DbKeyPairingImp;
import org.mypico.jpico.db.DbLensPairingAccessor;
import org.mypico.jpico.db.DbLensPairingImp;
import org.mypico.jpico.db.DbPairingImp;
import org.mypico.jpico.db.DbServiceImp;
import org.mypico.jpico.db.DbSessionImp;
import org.mypico.jpico.db.DbTerminalImp;
import org.mypico.jpico.db.DbVersioner;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;

/**
 * Database helper class used to manage the creation and upgrading of your database. This class also
 * usually provides the DAOs used by the other classes.
 *
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 * @author David Llewellyn-Jones <dl551@cam.ac.uk>
 * @author Graeme Jenkinson <gcj21@cam.ac.uk>
 * @author Max Spences <ms955@cam.ac.uk>
 */
final public class DbHelper extends OrmLiteSqliteOpenHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DbHelper.class.getSimpleName());

    // name of the database file for your application -- change to something appropriate for your
    // app
    private static final String DATABASE_NAME = "pico.db";

    private static DbHelper instance;

    public static DbHelper getInstance(final Context context) {
        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private Dao<DbServiceImp, Integer> serviceDao;
    private Dao<DbPairingImp, Integer> pairingDao;
    private Dao<DbKeyPairingImp, Integer> keyPairingDao;
    private Dao<DbLensPairingImp, Integer> lensPairingDao;
    private Dao<DbSessionImp, Integer> sessionDao;
    private Dao<DbTerminalImp, Integer> terminalDao;

    private DbKeyPairingAccessor keyPairingAccessor;
    private DbLensPairingAccessor lensPairingAccessor;

    /**
     * @deprecated use {@link #getInstance} instead.
     */
    @Deprecated
    public DbHelper(final Context context) {

        // This is an optimisation which reduces the time taken to creates the
        // DAOs, but the config file must be re-generated everytime the database
        // schema changes, so leave it turned off for the time being.

        // super(context, DATABASE_NAME, null, DATABASE_VERSION, R.raw.ormlite_config);

        super(context, DATABASE_NAME, null, DbVersioner.CURRENT_VERSION);
        LOGGER.debug("DatabaseHelper constructed");
    }

    public static File getDatabaseFile() {
        return getDatabaseFile(PicoApplication.getContext());
    }

    public static File getDatabaseFile(Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

    /**
     * This is called when the database is first created. Creates the required database tables.
     */
    @Override
    public void onCreate(final SQLiteDatabase db, final ConnectionSource connectionSource) {
        try {
            DbVersioner.createDatabase(connectionSource);
        } catch (SQLException e) {
            LOGGER.error("Database creation failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Enable foreign key constraints:
     *
     * @see <a href="http://stackoverflow.com/questions/6789075/deleting-using-ormlite-on-android}">Stack Overflow</a>
     */
    @Override
    public void onOpen(final SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.setForeignKeyConstraintsEnabled(true);
        }
    }

    /**
     * This is called when your application is upgraded and it has a higher version number.
     * Updates the required database tables.
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final ConnectionSource connectionSource,
                          final int oldVersion, final int newVersion) {
        LOGGER.info("Upgrading database from version " + oldVersion + " to " + newVersion);
        assert (newVersion == DbVersioner.CURRENT_VERSION);
        try {
            DbVersioner.upgradeDatabase(connectionSource, oldVersion);
        } catch (SQLException e) {
            LOGGER.error("Database upgrade failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the data access object for the {@link DbServiceImp} service
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbServiceImp, Integer> getServiceDao() throws SQLException {
        if (serviceDao == null) {
            serviceDao = getDao(DbServiceImp.class);
        }
        return serviceDao;
    }

    /**
     * Get the data access object for the {@link DbPairingImp} pairing
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbPairingImp, Integer> getPairingDao() throws SQLException {
        if (pairingDao == null) {
            pairingDao = getDao(DbPairingImp.class);
        }
        return pairingDao;
    }

    /**
     * Get the data access object for the {@link DbKeyPairingImp} key pairing
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbKeyPairingImp, Integer> getKeyPairingDao() throws SQLException {
        if (keyPairingDao == null) {
            keyPairingDao = getDao(DbKeyPairingImp.class);
        }
        return keyPairingDao;
    }

    /**
     * Get the data access object for the {@link DbLensPairingImp} lens pairing
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbLensPairingImp, Integer> getLensPairingDao() throws SQLException {
        if (lensPairingDao == null) {
            lensPairingDao = getDao(DbLensPairingImp.class);
        }
        return lensPairingDao;
    }

    /**
     * Get the data access object for the {@link DbSessionImp} session
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbSessionImp, Integer> getSessionDao() throws SQLException {
        if (sessionDao == null) {
            sessionDao = getDao(DbSessionImp.class);
        }
        return sessionDao;
    }

    /**
     * Get the data access object for the {@link DbTerminalImp} terminal
     * implementation.
     *
     * @return The data access object.
     * @throws SQLException if an error occurs accessing the database.
     */
    Dao<DbTerminalImp, Integer> getTerminalDao() throws SQLException {
        if (terminalDao == null) {
            terminalDao = getDao(DbTerminalImp.class);
        }
        return terminalDao;
    }

    /**
     * Get the accessor for accessing {@link org.mypico.jpico.data.pairing.KeyPairing} key pairings
     * from the database.
     *
     * @return The accessor object.
     * @throws SQLException if an error occurs accessing the database.
     */
    public KeyPairingAccessor getKeyPairingAccessor() throws SQLException {
        if (keyPairingAccessor == null) {
            keyPairingAccessor = new DbKeyPairingAccessor(
                getKeyPairingDao(), getPairingDao(), getServiceDao());
        }
        return keyPairingAccessor;
    }

    /**
     * Get the accessor for accessing {@link org.mypico.jpico.data.pairing.LensPairing} lens
     * pairings from the database.
     *
     * @return The accessor object.
     * @throws SQLException if an error occurs accessing the database.
     */
    public LensPairingAccessor getLensPairingAccessor() throws SQLException {
        if (lensPairingAccessor == null) {
            lensPairingAccessor = new DbLensPairingAccessor(
                getLensPairingDao(), getPairingDao(), getServiceDao());
        }
        return lensPairingAccessor;
    }
}