# PinPoint - Initial Release (v0.1.0)

**Features:**
- Jetpack Compose UI with TopAppBar (dropdown menu, Dark Mode toggle, Exit) and BottomAppBar ("Parse Image")
- Score list display showing headers and player/game scores
- Light/Dark theme support with smooth animated transitions
- Camera and gallery image capture with UCrop cropping integration
- ML Kit OCR text recognition and parsing into structured player scores
- State management via `MainViewModel` (`StateFlow`) for images, loading, errors, and recognized text
- Permissions handling for camera access
- Helper functions for parsing OCR text into table columns with fuzzy header detection
- Basic theming via `PinPointTheme`
- Initial workflows for image pick, take, and crop

