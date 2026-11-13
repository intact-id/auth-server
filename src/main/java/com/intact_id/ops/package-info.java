/**
 * Operations: pings the services auth-server depends on every minute and keeps 30 days of results, so
 * super admins can see what is up, how fast it answers and its uptime.
 */
@ApplicationModule(displayName = "Operations")
package com.intact_id.ops;

import org.springframework.modulith.ApplicationModule;
