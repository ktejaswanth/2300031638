import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { initDatabase } from './db.js';
import notificationRoutes from './routes/notificationRoutes.js';
import { loggerService } from './services/loggerService.js';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 8082;

// Middlewares
app.use(cors());
app.use(express.json());

// Log incoming REST API transactions
app.use((req, res, next) => {
  loggerService.log('backend', 'info', 'route', `HTTP Request: ${req.method} ${req.originalUrl}`);
  next();
});

// Mount Routes
app.use('/api/notifications', notificationRoutes);

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'HEALTHY', microservice: 'notification_app_be' });
});

// Startup Routine
const startServer = async () => {
  try {
    // 1. Initialize relational database schemas, indexes, and seed values
    await initDatabase();

    // 2. Start HTTP server listening
    app.listen(PORT, () => {
      loggerService.log('backend', 'info', 'config', `Centralized Notification Microservice running on port ${PORT}`);
      console.log(`\n======================================================`);
      console.log(`🚀 Notification Backend running at http://localhost:${PORT}`);
      console.log(`🏥 Health check: http://localhost:${PORT}/health`);
      console.log(`======================================================\n`);
    });
  } catch (error) {
    loggerService.log('backend', 'fatal', 'config', `Microservice startup sequence aborted: ${error.message}`);
    process.exit(1);
  }
};

startServer();
