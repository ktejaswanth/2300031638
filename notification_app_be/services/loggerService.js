import dotenv from 'dotenv';
dotenv.config();

const EVALUATION_LOG_URL = 'http://4.224.186.213/evaluation-service/logs';
const BEARER_TOKEN = process.env.EVALUATION_TOKEN || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjcyODksImlhdCI6MTc4MDEyNjM4OSwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6IjY0OTU3MGQ2LTg0Y2YtNDNlZC1iNGJlLTNmY2QwZThlNTNhYiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.imciygo2RVkMBB7uhymauVgiD0ZuyELYvmdCAmnbq8k';

const VALID_STACKS = ['backend', 'frontend'];
const VALID_LEVELS = ['debug', 'info', 'warn', 'error', 'fatal'];

const VALID_BACKEND_PACKAGES = ['cache', 'controller', 'cron_job', 'db', 'domain', 'handler', 'repository', 'route', 'service'];
const VALID_FRONTEND_PACKAGES = ['api', 'component', 'hook', 'page', 'state', 'style'];
const VALID_COMMON_PACKAGES = ['auth', 'config', 'middleware', 'utils'];

/**
 * Validates and dispatches telemetry logs asynchronously to the remote evaluation server.
 */
class LoggerService {
  log(stack, level, packageName, message) {
    const stackLower = (stack || '').toLowerCase();
    const levelLower = (level || '').toLowerCase();
    const pkgLower = (packageName || '').toLowerCase();

    // 1. Validate stack
    if (!VALID_STACKS.includes(stackLower)) {
      console.error(`[LoggerService ERROR] Invalid stack: ${stack}`);
      return;
    }

    // 2. Validate level
    if (!VALID_LEVELS.includes(levelLower)) {
      console.error(`[LoggerService ERROR] Invalid level: ${level}`);
      return;
    }

    // 3. Validate package
    const isCommon = VALID_COMMON_PACKAGES.includes(pkgLower);
    const isBackendValid = stackLower === 'backend' && VALID_BACKEND_PACKAGES.includes(pkgLower);
    const isFrontendValid = stackLower === 'frontend' && VALID_FRONTEND_PACKAGES.includes(pkgLower);

    if (!isCommon && !isBackendValid && !isFrontendValid) {
      console.error(`[LoggerService ERROR] Invalid package "${packageName}" for stack "${stack}".`);
      return;
    }

    // 4. Print formatted local log
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] [${stackLower.toUpperCase()}] [${levelLower.toUpperCase()}] [${pkgLower}] - ${message}`);

    // 5. Send asynchronous POST HTTP call to telemetry log server
    try {
      let truncatedMessage = message || '';
      if (truncatedMessage.length > 48) {
        truncatedMessage = truncatedMessage.substring(0, 45) + '...';
      }

      const payload = {
        stack: stackLower,
        level: levelLower,
        package: pkgLower,
        message: truncatedMessage
      };

      // Native fetch in Node 18+ sends log asynchronously
      fetch(EVALUATION_LOG_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${BEARER_TOKEN}`
        },
        body: JSON.stringify(payload)
      })
      .then(response => {
        if (!response.ok) {
          response.text().then(errText => {
            console.warn(`[LoggerService WARNING] Log server returned HTTP ${response.status}: ${errText}`);
          });
        }
      })
      .catch(err => {
        console.error(`[LoggerService ERROR] Telemetry delivery failed: ${err.message}`);
      });
    } catch (e) {
      console.error(`[LoggerService ERROR] Exception occurred initiating post: ${e.message}`);
    }
  }
}

export const loggerService = new LoggerService();
