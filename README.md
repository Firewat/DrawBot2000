# CNC Plotter "DrawBot 2000" with Android App Integration

Android app to control a pen plotter via Bluetooth using GRBL firmware.

## What it does

- Authentification with Firebase (Login & Signup)
- Connects to Arduino pen plotter over Bluetooth (HC-06)
- Converts G-code files for pen plotting 
- Real-time terminal output and progress
- Calibrates stepper motors (28BYJ-48)

## Hardware needed

- Arduino Uno with GRBL firmware (for our projectwe used: https://github.com/TGit-Tech/GRBL-28byj-48)
- 3x 28BYJ-48 stepper motors (X, Y, Z axes)
- HC-06 Bluetooth module
- Pen lifting mechanism
- 3D Printed parts found here: https://www.thingiverse.com/thing:4579436

## How to use

1. Install the APK on your Android phone
2. Pair with HC-06 Bluetooth module
3. Connect in the app
4. Upload G-code file or write commands
5. Press Send to start drawing

## Settings

Go to Settings to configure motors:
- Steps per mm: 65.0 (for 28BYJ-48)
- Max speed: 800 mm/min
- Acceleration: 10 mm/sec²

## G-code conversion

The app automatically converts generated G-code from https://sameer.github.io/svg2gcode/:
```
G0 X10 Y20  →  G1 X10 Y20 Z1  (pen up)
G1 X30 Y40  →  G1 X30 Y40 Z0  (pen down)
```
## First Drawing Test (Simple test square)

G0 X0 Y0      ; Move to origin (pen up)
G1 X10 Y0     ; Draw right side (pen down) 
G1 X10 Y10    ; Draw top side
G1 X0 Y10     ; Draw left side
G1 X0 Y0      ; Draw bottom side
G0 X0 Y0      ; Return to origin (pen up)

## Building

1. Clone this repo
2. Open in Android Studio
3. Build and install


### Key Implementation Classes

**BluetoothTerminalActivity**
- Core G-code processing and real-time execution
- Terminal interface
- Progress tracking and time estimation (very rough)

**BluetoothHelper** 
- Low-level Bluetooth communication
- Connection management and error recovery
- Message parsing and command queuing

**SettingsActivity**
- GRBL parameter configuration
- Motor calibration interface
- Profile Deletion

### Areas of Improvements
- Additional G-code format support
- UI/UX improvements and accessibility features
- Performance optimizations and memory management
- Hardware compatibility testing

## License

MIT
