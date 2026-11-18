// firebase-config.js - Primary + 5 Fallback Firebase Account Pool Config
const firebaseAccountPool = [
    {
        name: "Primary Account",
        databaseURL: "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app",
        apiKey: "AIzaSyPrimaryDefaultKey12345"
    },
    {
        name: "Fallback Account #1",
        databaseURL: "https://homemonitor-fallback-1-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyOne12345"
    },
    {
        name: "Fallback Account #2",
        databaseURL: "https://homemonitor-fallback-2-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyTwo12345"
    },
    {
        name: "Fallback Account #3",
        databaseURL: "https://homemonitor-fallback-3-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyThree12345"
    },
    {
        name: "Fallback Account #4",
        databaseURL: "https://homemonitor-fallback-4-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyFour12345"
    },
    {
        name: "Fallback Account #5",
        databaseURL: "https://homemonitor-fallback-5-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyFive12345"
    }
];

let activeFirebaseIndex = 0;

function initActiveFirebaseInstance() {
    const config = firebaseAccountPool[activeFirebaseIndex];
    if (firebase.apps.length > 0) {
        firebase.app().delete();
    }
    firebase.initializeApp({
        databaseURL: config.databaseURL,
        apiKey: config.apiKey
    });
    console.log(`[Firebase C2] Active Instance: ${config.name} (${config.databaseURL})`);
}

// Initializing Primary Account
initActiveFirebaseInstance();
