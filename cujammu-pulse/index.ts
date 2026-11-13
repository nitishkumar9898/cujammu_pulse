import { registerRootComponent } from 'expo';
import { randomUUID } from 'expo-crypto';
import { configureRequestKeyGenerator } from './src/request-key';
import App from './src/App';
configureRequestKeyGenerator(randomUUID);
registerRootComponent(App);
