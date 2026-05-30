import { queryAll, queryGet, queryRun } from '../db.js';
import { loggerService } from '../services/loggerService.js';

// Minimal Binary Min-Heap Implementation in JavaScript matching Java's PriorityQueue
class MinHeap {
  constructor(comparator) {
    this.heap = [];
    this.comparator = comparator;
  }

  size() {
    return this.heap.length;
  }

  offer(val) {
    this.heap.push(val);
    this.bubbleUp(this.heap.length - 1);
  }

  poll() {
    if (this.heap.length === 0) return null;
    const root = this.heap[0];
    const end = this.heap.pop();
    if (this.heap.length > 0) {
      this.heap[0] = end;
      this.sinkDown(0);
    }
    return root;
  }

  peek() {
    return this.heap[0] || null;
  }

  bubbleUp(index) {
    while (index > 0) {
      const parentIndex = Math.floor((index - 1) / 2);
      if (this.comparator(this.heap[index], this.heap[parentIndex]) >= 0) break;
      this.swap(index, parentIndex);
      index = parentIndex;
    }
  }

  sinkDown(index) {
    const length = this.heap.length;
    while (true) {
      let left = 2 * index + 1;
      let right = 2 * index + 2;
      let smallest = index;

      if (left < length && this.comparator(this.heap[left], this.heap[smallest]) < 0) {
        smallest = left;
      }
      if (right < length && this.comparator(this.heap[right], this.heap[smallest]) < 0) {
        smallest = right;
      }

      if (smallest === index) break;
      this.swap(index, smallest);
      index = smallest;
    }
  }

  swap(i, j) {
    const temp = this.heap[i];
    this.heap[i] = this.heap[j];
    this.heap[j] = temp;
  }

  toArray() {
    return [...this.heap];
  }
}

/**
 * Controller to manage student notifications
 */
export const dispatchNotification = async (req, res) => {
  loggerService.log('backend', 'info', 'controller', 'dispatchNotification API called');
  const { userId, notificationTypeId, title, message } = req.body;

  if (!userId || !notificationTypeId || !title || !message) {
    loggerService.log('backend', 'warn', 'controller', 'dispatchNotification failed: Missing payload fields');
    return res.status(400).json({ error: 'Missing required fields: userId, notificationTypeId, title, message' });
  }

  try {
    // Verify user exists
    const user = await queryGet('SELECT * FROM users WHERE id = ?', [userId]);
    if (!user) {
      loggerService.log('backend', 'warn', 'controller', `dispatchNotification failed: User ${userId} not found`);
      return res.status(404).json({ error: `User with id ${userId} not found` });
    }

    // Verify notification type exists
    const notifType = await queryGet('SELECT * FROM notification_types WHERE id = ?', [notificationTypeId]);
    if (!notifType) {
      loggerService.log('backend', 'warn', 'controller', `dispatchNotification failed: Notification type ${notificationTypeId} not found`);
      return res.status(404).json({ error: `Notification type with id ${notificationTypeId} not found` });
    }

    const createdTime = new Date().toISOString().slice(0, 19).replace('T', ' ');

    const result = await queryRun(
      'INSERT INTO notifications (title, message, is_read, created_at, user_id, notification_type_id) VALUES (?, ?, 0, ?, ?, ?)',
      [title, message, createdTime, userId, notificationTypeId]
    );

    const inserted = {
      id: result.id,
      title,
      message,
      isRead: false,
      createdAt: createdTime,
      userId,
      notificationTypeId
    };

    loggerService.log('backend', 'info', 'controller', `Notification successfully dispatched. ID: ${result.id}`);
    res.status(201).json(inserted);
  } catch (err) {
    loggerService.log('backend', 'error', 'controller', `dispatchNotification error: ${err.message}`);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
  }
};

export const getStudentNotifications = async (req, res) => {
  loggerService.log('backend', 'info', 'controller', 'getStudentNotifications API called');
  const studentId = req.query.studentId;
  
  if (!studentId) {
    loggerService.log('backend', 'warn', 'controller', 'getStudentNotifications failed: Missing studentId parameter');
    return res.status(400).json({ error: 'Missing required query parameter: studentId' });
  }

  // Support Stage 3 Pagination (limit & offset)
  const limit = parseInt(req.query.limit) || 20;
  const offset = parseInt(req.query.offset) || 0;

  try {
    loggerService.log('backend', 'info', 'controller', `Executing paginated query with B-Tree indexes for student: ${studentId}`);
    
    // Joint query returning type name alongside notification fields
    const query = `
      SELECT n.id, n.title, n.message, n.is_read AS isRead, n.created_at AS createdAt, t.type_name AS notificationType
      FROM notifications n
      INNER JOIN notification_types t ON n.notification_type_id = t.id
      WHERE n.user_id = ?
      ORDER BY n.created_at DESC
      LIMIT ? OFFSET ?
    `;

    const rows = await queryAll(query, [studentId, limit, offset]);

    // Map database binary int read status to boolean
    const formattedRows = rows.map(r => ({
      ...r,
      isRead: r.isRead === 1
    }));

    loggerService.log('backend', 'info', 'controller', `Retrieved ${formattedRows.length} records for student ${studentId}`);
    res.status(200).json(formattedRows);
  } catch (err) {
    loggerService.log('backend', 'error', 'controller', `getStudentNotifications error: ${err.message}`);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
  }
};

export const updateNotificationStatus = async (req, res) => {
  loggerService.log('backend', 'info', 'controller', 'updateNotificationStatus API called');
  const id = req.params.id;
  const { isRead } = req.body;

  if (isRead === undefined) {
    loggerService.log('backend', 'warn', 'controller', 'updateNotificationStatus failed: Missing isRead in request body');
    return res.status(400).json({ error: 'Missing field in body: isRead' });
  }

  try {
    const current = await queryGet('SELECT * FROM notifications WHERE id = ?', [id]);
    if (!current) {
      loggerService.log('backend', 'warn', 'controller', `updateNotificationStatus failed: Notification ID ${id} not found`);
      return res.status(404).json({ error: `Notification with id ${id} not found` });
    }

    const binaryReadStatus = isRead ? 1 : 0;
    await queryRun('UPDATE notifications SET is_read = ? WHERE id = ?', [binaryReadStatus, id]);

    loggerService.log('backend', 'info', 'controller', `Notification ID ${id} isRead updated to ${isRead}`);
    res.status(200).json({
      id: parseInt(id),
      isRead
    });
  } catch (err) {
    loggerService.log('backend', 'error', 'controller', `updateNotificationStatus error: ${err.message}`);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
  }
};

export const deleteNotification = async (req, res) => {
  loggerService.log('backend', 'info', 'controller', 'deleteNotification API called');
  const id = req.params.id;

  try {
    const current = await queryGet('SELECT * FROM notifications WHERE id = ?', [id]);
    if (!current) {
      loggerService.log('backend', 'warn', 'controller', `deleteNotification failed: Notification ID ${id} not found`);
      return res.status(404).json({ error: `Notification with id ${id} not found` });
    }

    await queryRun('DELETE FROM notifications WHERE id = ?', [id]);
    
    loggerService.log('backend', 'info', 'controller', `Notification ID ${id} successfully deleted/archived`);
    res.status(204).end();
  } catch (err) {
    loggerService.log('backend', 'error', 'controller', `deleteNotification error: ${err.message}`);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
  }
};

// Stage 6 - Priority Heap Routing Algorithm Top-10 Selector
export const getPriorityNotifications = async (req, res) => {
  loggerService.log('backend', 'info', 'controller', 'getPriorityNotifications called (Stage 6 Heap Selection)');
  
  try {
    // 1. Fetch all raw active notifications along with priority weights
    const query = `
      SELECT n.id, n.title, n.message, n.is_read AS isRead, n.created_at AS createdAt,
             t.type_name AS type, t.priority_weight AS priorityWeight
      FROM notifications n
      INNER JOIN notification_types t ON n.notification_type_id = t.id
    `;

    const all = await queryAll(query);
    loggerService.log('backend', 'info', 'controller', `Heap sorting stream loaded: ${all.length} entries`);

    // 2. Instantiate Min-Heap (size-capped Priority Queue window)
    // Comparator ranks lower priority elements (ascending weight / oldest timestamp) at the root
    const minHeapComparator = (a, b) => {
      if (a.priorityWeight !== b.priorityWeight) {
        return a.priorityWeight - b.priorityWeight; // Higher weight goes down, lowest stays at top/root
      }
      // If weights match, older date strings go down (Min-Heap keeps oldest at top for eviction)
      return a.createdAt.localeCompare(b.createdAt);
    };

    const minHeap = new MinHeap(minHeapComparator);

    // 3. Stream-process the dataset maintaining size K=10
    for (const item of all) {
      minHeap.offer(item);
      if (minHeap.size() > 10) {
        const evicted = minHeap.poll();
        loggerService.log('backend', 'debug', 'service', `Evicted lower priority: ${evicted.id}`);
      }
    }

    // 4. Retrieve remaining top-10 elements and sort descending for user presentation (highest priority first)
    const topTen = minHeap.toArray();
    topTen.sort((a, b) => {
      if (a.priorityWeight !== b.priorityWeight) {
        return b.priorityWeight - a.priorityWeight; // Descending
      }
      return b.createdAt.localeCompare(a.createdAt); // Descending (recency)
    });

    // Format fields correctly matching the API Response standard
    const result = topTen.map(item => ({
      ID: String(item.id),
      Type: item.type,
      Message: item.message,
      Timestamp: item.createdAt
    }));

    loggerService.log('backend', 'info', 'controller', 'Heap filter completed. Returning top-10 priority items.');
    res.status(200).json(result);
  } catch (err) {
    loggerService.log('backend', 'error', 'controller', `getPriorityNotifications failed: ${err.message}`);
    res.status(500).json({ error: 'Internal Server Error', details: err.message });
  }
};
