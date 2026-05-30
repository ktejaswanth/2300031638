import sqlite3 from 'sqlite3';
import { loggerService } from './services/loggerService.js';

// Activate verbose debugging if needed
const sqlite = sqlite3.verbose();

// Create local file-based SQLite database
const db = new sqlite.Database('./notifications.db', (err) => {
  if (err) {
    loggerService.log('backend', 'error', 'db', `SQLite Database connection failed: ${err.message}`);
  } else {
    loggerService.log('backend', 'info', 'db', 'SQLite Database connection initialized successfully (notifications.db)');
  }
});

// Promisified DB helper functions
export const queryAll = (sql, params = []) => {
  return new Promise((resolve, reject) => {
    db.all(sql, params, (err, rows) => {
      if (err) reject(err);
      else resolve(rows);
    });
  });
};

export const queryGet = (sql, params = []) => {
  return new Promise((resolve, reject) => {
    db.get(sql, params, (err, row) => {
      if (err) reject(err);
      else resolve(row);
    });
  });
};

export const queryRun = (sql, params = []) => {
  return new Promise((resolve, reject) => {
    db.run(sql, params, function (err) {
      if (err) reject(err);
      else resolve({ id: this.lastID, changes: this.changes });
    });
  });
};

// Database Schema Initializer & Seeder
export const initDatabase = () => {
  return new Promise((resolve, reject) => {
    db.serialize(() => {
      try {
        loggerService.log('backend', 'info', 'db', 'Creating table schemas in SQLite...');

        // 1. Users Table
        db.run(`
          CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )
        `);

        // 2. NotificationTypes Table
        db.run(`
          CREATE TABLE IF NOT EXISTS notification_types (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type_name TEXT NOT NULL UNIQUE,
            priority_weight INTEGER NOT NULL DEFAULT 1
          )
        `);

        // 3. Notifications Table
        db.run(`
          CREATE TABLE IF NOT EXISTS notifications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            message TEXT NOT NULL,
            is_read INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            user_id INTEGER NOT NULL,
            notification_type_id INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (notification_type_id) REFERENCES notification_types(id)
          )
        `);

        loggerService.log('backend', 'info', 'db', 'Schemas built. Creating balanced search B-Tree indexes...');

        // Stage 3 - Performance Indexes
        db.run(`CREATE INDEX IF NOT EXISTS idx_student ON notifications(user_id)`);
        db.run(`CREATE INDEX IF NOT EXISTS idx_type ON notifications(notification_type_id)`);
        db.run(`CREATE INDEX IF NOT EXISTS idx_read ON notifications(is_read)`);

        loggerService.log('backend', 'info', 'db', 'Seeding mock initial datasets...');

        // Seed Notification Types
        db.run(`INSERT OR IGNORE INTO notification_types (id, type_name, priority_weight) VALUES (1, 'Event', 1)`);
        db.run(`INSERT OR IGNORE INTO notification_types (id, type_name, priority_weight) VALUES (2, 'Result', 2)`);
        db.run(`INSERT OR IGNORE INTO notification_types (id, type_name, priority_weight) VALUES (3, 'Placement', 3)`);

        // Seed initial users
        db.run(`INSERT OR IGNORE INTO users (id, name, email) VALUES (101, 'Student One', 'student101@example.com')`);
        db.run(`INSERT OR IGNORE INTO users (id, name, email) VALUES (102, 'Afford Admin', 'admin@affordmedical.com')`);
        db.run(`INSERT OR IGNORE INTO users (id, name, email) VALUES (103, 'John Doe', 'john.doe@gmail.com')`);

        // Seed a sample notification to start
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500001, 'Exam Results Published', 'The final semester results have been uploaded.', 0, '2026-05-30 11:45:00', 101, 2)
        `);
        
        // Seed multiple notifications to demonstrate min-heap priority queue filtering
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500002, 'Hackathon Event Scheduled', 'Join us for the Afford Medical hackathon.', 0, '2026-05-30 10:15:00', 101, 1)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500003, 'Amazon Placement Drive', 'Submit your resumes by tomorrow midnight.', 0, '2026-05-30 12:00:00', 101, 3)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500004, 'Library Due Alert', 'Please return your rented books.', 0, '2026-05-29 09:00:00', 101, 1)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500005, 'Scholarship Results Out', 'Check portal to see approved scholarship lists.', 0, '2026-05-28 14:00:00', 101, 2)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500006, 'Google Internship Offer', 'Applications are open for SWE intern roles.', 0, '2026-05-30 12:30:00', 101, 3)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500007, 'Maths Quiz Postponed', 'The quiz scheduled for Monday is postponed.', 0, '2026-05-30 08:00:00', 101, 1)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500008, 'Microsoft Recruiter Visit', 'Microsoft recruiters visiting the campus on Monday.', 0, '2026-05-30 11:30:00', 101, 3)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500009, 'Physics Lab Grades', 'Physics lab grades have been posted to portal.', 0, '2026-05-29 16:00:00', 101, 2)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500010, 'Sports Day Registration', 'Register for the track and field events.', 0, '2026-05-30 07:00:00', 101, 1)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500011, 'Uber Interview Invites', 'Uber Swe selection shortlists are out.', 0, '2026-05-30 12:45:00', 101, 3)
        `);
        db.run(`
          INSERT OR IGNORE INTO notifications (id, title, message, is_read, created_at, user_id, notification_type_id)
          VALUES (500012, 'Annual Day Fest', 'Ticket sales for annual fest have begun.', 0, '2026-05-30 09:30:00', 101, 1)
        `);

        loggerService.log('backend', 'info', 'db', 'Seeded initial records and indexes. Ready to serve.');
        resolve();
      } catch (err) {
        loggerService.log('backend', 'error', 'db', `Seeding tables failed: ${err.message}`);
        reject(err);
      }
    });
  });
};

export default db;
