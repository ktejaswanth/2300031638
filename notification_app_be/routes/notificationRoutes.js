import express from 'express';
import {
  dispatchNotification,
  getStudentNotifications,
  updateNotificationStatus,
  deleteNotification,
  getPriorityNotifications
} from '../controllers/notificationController.js';

const router = express.Router();

// Define routes conforming to System Design Specifications
router.post('/', dispatchNotification);
router.get('/', getStudentNotifications);
router.put('/:id', updateNotificationStatus);
router.delete('/:id', deleteNotification);

// Stage 6 - Priority Heap Endpoint
router.get('/priority', getPriorityNotifications);

export default router;
