# AegisLayer - Android System Control Layer

AegisLayer is a modular, event-driven user-space daemon for Android behavior control. It introduces policy-based adaptive device behavior management by acting as an OS intelligence layer.

## Architecture

AegisLayer runs as a foreground service and consists of four main layers:

1. **System Event Monitor (Event Processing Layer)**: Captures system events like app state changes, battery status, notification events, etc.
2. **Context Builder**: Combines real-time events into a context snapshot.
3. **Rule Execution Engine (Decision Engine)**: Evaluates the context snapshot against defined rules (JSON-based) to decide what actions to take.
4. **Action Executor (System Action Controller)**: Executes the defined system actions (e.g., toggle DND, restrict background apps).

## Features

- **System Event Monitor**: Listens for system changes using `UsageStatsManager`, `BroadcastReceivers`, and `NotificationListenerService`.
- **Rule Engine**: Local rule-based logic processor that resolves rule priority.
- **Action Control**: Dispatches system adjustments safely.
- **Trace Engine**: Full logging system that records decisions and matches.

## Core Structure
- `com.aegislayer.daemon.service`: The main persistent Foreground Service (`SystemControlService`).
- `com.aegislayer.daemon.engine`: The brain (`RuleEngine`, `ContextBuilder`).
- `com.aegislayer.daemon.actions`: System changers (`ActionExecutor`).
- `com.aegislayer.daemon.receivers`: System hooks (`EventProcessor`).
- `com.aegislayer.daemon.models`: Data models (`Rule`, `Condition`).

## Development
To build the project:
1. Open this directory in Android Studio.
2. Sync the project with Gradle Files.
3. Build the `app` module.
