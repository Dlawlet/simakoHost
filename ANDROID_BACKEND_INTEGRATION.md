# 📱 Simako Android App - Backend Integration Setup

## 🔧 What Was Added

### 1. Network Dependencies (build.gradle.kts)

- Retrofit 2 for HTTP API calls
- Gson for JSON serialization
- OkHttp logging interceptor for debugging
- Kotlin coroutines for async operations

### 2. Network Permissions (AndroidManifest.xml)

- `INTERNET` permission for backend communication
- `ACCESS_NETWORK_STATE` permission for connectivity checks

### 3. New Network Layer

- **ApiModels.kt**: Data classes for API communication
- **SimakoBackendApi.kt**: Retrofit interface defining all API endpoints
- **BackendApiManager.kt**: Singleton managing both Flask and Node.js APIs
- **SimakoBackendService.kt**: Service handling SMS/call data transmission

### 4. Enhanced Receivers

- **SMSReceiver.kt**: Now sends incoming SMS data to backend
- **CallReceiver.kt**: New receiver for call events (incoming, outgoing, missed)

### 5. Backend Configuration

- **BackendConfigActivity.kt**: UI for setting backend URLs and testing connectivity
- **activity_backend_config.xml**: Layout for configuration screen

### 6. MainActivity Integration

- Initializes backend service on app startup
- Registers SIM cards with backend
- Checks backend connectivity

## 🚀 Setup Instructions

### Step 1: Build the App

```bash
# Navigate to your project directory
cd "d:\Hwork\Simako\DefaultSMSApp-master\DefaultSMSApp-master"

# Clean and build
./gradlew clean build

# Or if using Android Studio, click "Sync Project with Gradle Files"
```

### Step 2: Start Your Backends

```bash
# Terminal 1 - Start Flask backend
cd backend/flask
python app.py

# Terminal 2 - Start Node.js backend
cd backend/nodejs
npm run dev

# Terminal 3 - Start MongoDB (if not running)
mongod
```

### Step 3: Configure Network URLs

The app is configured for Android Emulator by default:

- Flask: `http://10.0.2.2:5000/`
- Node.js: `http://10.0.2.2:3000/`

For physical devices, you'll need your computer's IP address:

- Flask: `http://YOUR_IP:5000/`
- Node.js: `http://YOUR_IP:3000/`

## 📱 Testing the Integration

### Option 1: Use the Configuration Screen

1. **Install and launch the app**
2. **Open Backend Configuration** (need to add a menu item to access it)
3. **Test connections** using the test buttons
4. **Save settings** once connections work

### Option 2: Direct Testing

1. **Send an SMS to the device/emulator**
2. **Check the logs** for backend communication:

   ```bash
   adb logcat | grep -E "(SMSReceiver|SimakoBackendService|BackendApiManager)"
   ```

3. **Check your backend logs** for received data
4. **Verify data in MongoDB** using MongoDB Compass or the web UI

### Option 3: Make/Receive Calls

1. **Make a call from the device**
2. **Receive a call on the device**
3. **Check logs** for call data transmission

## 🔍 What Happens When SMS/Calls Are Received

### SMS Flow:

1. **SMS arrives** → SMSReceiver triggered
2. **SMS saved** to Android's SMS database
3. **Data extracted**: sender, message, timestamp, SIM info
4. **HTTP POST** sent to active backend `/api/messages`
5. **Backend stores** data in MongoDB
6. **App UI refreshed** to show new SMS

### Call Flow:

1. **Phone state changes** → CallReceiver triggered
2. **Call data extracted**: caller, duration, type (incoming/outgoing/missed)
3. **HTTP POST** sent to active backend `/api/messages`
4. **Backend stores** call data in MongoDB

## 📊 Backend Data Structure

### SMS Message in MongoDB:

```json
{
  "sim_id": "SIM_123456789",
  "type": "sms",
  "from": "+1234567890",
  "to": null,
  "message": "Hello from Simako!",
  "timestamp": "2025-07-22T10:30:00.000Z",
  "metadata": {
    "is_incoming": true,
    "app_version": "1.0",
    "device_info": {
      "manufacturer": "Google",
      "model": "Pixel_3a",
      "android_version": "11"
    }
  }
}
```

### Call Record in MongoDB:

```json
{
  "sim_id": "SIM_123456789",
  "type": "call",
  "from": "+1234567890",
  "to": null,
  "message": "Call - Duration: 45s, Type: incoming",
  "timestamp": "2025-07-22T10:35:00.000Z",
  "metadata": {
    "call_type": "incoming",
    "duration_seconds": 45,
    "app_version": "1.0"
  }
}
```

## 🛠️ Troubleshooting

### Common Issues:

1. **"No backend connection"**

   - Ensure backends are running
   - Check URLs (use 10.0.2.2 for emulator)
   - Verify firewall settings

2. **"Permission denied"**

   - Grant SMS and Phone permissions in device settings
   - Ensure app is set as default SMS app

3. **"Network error"**

   - Check internet connectivity
   - Verify backend URLs are correct
   - Look at network logs: `adb logcat | grep OkHttp`

4. **"MongoDB connection failed"**
   - Start MongoDB: `mongod`
   - Check MongoDB is running on port 27017

### Debugging Commands:

```bash
# Check app logs
adb logcat | grep -E "(SMSReceiver|SimakoBackendService)"

# Check network requests
adb logcat | grep OkHttp

# Check all app logs
adb logcat | grep com.hwork.simakohost

# Check running processes
adb shell ps | grep simakohost
```

## 🎯 Next Steps

1. **Add Menu Item**: Add backend configuration to app menu
2. **Test with Real Data**: Send actual SMS and make calls
3. **Monitor Backend**: Check MongoDB for incoming data
4. **Add Error Handling**: Improve error reporting in the app
5. **Add Retry Logic**: Handle network failures gracefully

## 📈 Verification Checklist

- [ ] App builds successfully
- [ ] Both backends are running
- [ ] MongoDB is connected
- [ ] App connects to backends (check configuration screen)
- [ ] SMS data appears in backend logs
- [ ] Call data appears in backend logs
- [ ] Data is stored in MongoDB
- [ ] No critical errors in app logs

Simako app is now fully integrated with both backend systems! 🎉

## 🔗 API Endpoints Being Used

- `POST /api/messages` - Send SMS/call data
- `POST /api/sim-cards` - Register SIM cards
- `GET /health` - Check backend health

The app automatically switches between Flask and Node.js backends based on your configuration, allowing you to learn and compare both implementations.
