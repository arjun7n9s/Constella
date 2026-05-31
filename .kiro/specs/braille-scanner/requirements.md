# Requirements Document

## Introduction

The Braille Scanner is an accessibility-focused, native Android application that uses the phone camera to scan real, physical Braille and convert it into English text and spoken audio in real time or near real time. The application reads both machine-embossed Braille and handwritten (slate-and-stylus) Braille. The core challenge is computer vision and accessibility, not digital Unicode translation: the System must detect real, physical raised dots from a camera image under everyday conditions, group those dots into Braille cells, recognize the cell patterns, translate them into English, and read the result aloud.

The primary user is a caregiver, teacher, or accessibility worker who cannot read Braille by touch and needs to understand physical Braille documents and notes produced by students or clients. The application is also intended to be usable by low-vision users directly. Both audiences are served by an accessibility-first interface with spoken guidance.

All processing runs 100% on the device with no backend server. The application must work offline and continue to work at any time, indefinitely, after handoff, with zero dependency on any server the developer must keep running or paying for. By default, camera frames must never leave the device. Image data may leave the device only when an optional cloud mode is explicitly enabled with the Operator's consent and applicable institutional approval. An optional cloud-boost mode is documented only as a future, opt-in, non-dependency option; the core product is fully functional without it.

Accuracy is the top priority. The System is honest about its accuracy tiers: machine-embossed Braille is the headline, high-accuracy tier, while handwritten Braille is a clearly labeled, lower-confidence second tier so the Operator knows when to trust the output. Automatic Braille grade detection is treated as an estimate and is always backed by a one-tap manual override.

The recognition pipeline is: Camera Input (with controllable torch at a raking angle plus macro focus to make embossed dots cast detectable shadows) → Image Preprocessing (perspective correction, lighting normalization) → Braille Dot Detection (bundled ML object-detection model exported to TFLite or ONNX) → Braille Cell Segmentation (2x3 grid grouping) → Braille Pattern Recognition → English Text Conversion (liblouis compiled for Android) → Text-to-Speech Output (Android built-in offline TTS).

## Glossary

- **System**: The Braille Scanner Android application as a whole, including all on-device modules and bundled assets.
- **Operator**: The person operating the System during a scan. This is typically a non-technical caregiver, teacher, or accessibility worker who cannot read Braille by touch, and may also be a low-vision user.
- **Installer**: The non-technical person who installs the Application_Package onto an Android device.
- **Camera_Module**: The System component that controls the device camera, torch, focus, and frame capture.
- **Torch**: The device camera flash LED, used as a controllable light source to create raking-angle illumination.
- **Raking_Light**: Low-angle illumination used to make white-on-white embossed dots cast detectable shadows.
- **Live_Mode**: The continuous scanning mode in which the System processes camera frames and produces output in near real time without an explicit capture action.
- **Capture_Mode**: The scan-and-read mode in which the Operator triggers capture of a single still frame for the most reliable recognition.
- **Alignment_Guide**: The System component that analyzes the live camera feed and produces spoken and visual guidance about document positioning, distance, steadiness, and lighting.
- **Image_Preprocessor**: The System component that applies perspective correction and lighting normalization to captured frames.
- **Dot_Detector**: The bundled machine-learning object-detection model (YOLO or Angelina-Reader style, exported to TFLite or ONNX) that detects individual Braille dots and/or cells from a preprocessed image.
- **Cell_Segmenter**: The System component that groups detected dots into Braille cells using a 2-by-3 grid and orders cells into reading order.
- **Pattern_Recognizer**: The System component that maps each segmented cell to a six-dot Braille pattern.
- **Translation_Engine**: The on-device liblouis library compiled for Android that converts recognized Braille patterns into English text.
- **Grade_Detector**: The System component that estimates whether scanned Braille is Grade 1 or Grade 2 English Braille.
- **Grade_Mode**: The active translation grade setting, which is one of Auto, Grade 1, or Grade 2.
- **Grade 1 Braille**: Uncontracted English Braille where each cell maps to a letter, number, or symbol.
- **Grade 2 Braille**: Contracted English Braille that uses contractions and abbreviations.
- **TTS_Engine**: The Android built-in TextToSpeech facility used to read recognized English text aloud offline.
- **Cloud_Speech_Service**: An optional, approved third-party online text-to-speech service that may provide enhanced speech functionality only when network connectivity is available and the Operator has explicitly enabled cloud features.
- **Recognized_Text**: The English text produced by the Translation_Engine from a scan.
- **Confidence_Score**: A numeric value between 0 and 1 representing the System's estimated reliability of a recognized dot, cell, character, or scan.
- **Embossed_Mode**: The scanning profile optimized for machine-embossed Braille; the high-accuracy tier.
- **Handwritten_Mode**: The scanning profile optimized for handwritten slate-and-stylus Braille; the lower-confidence second tier.
- **Scanning_Mode**: The active scanning profile, which is one of Embossed_Mode or Handwritten_Mode.
- **Theming_Layer**: The architectural separation between UI structure and component contracts on one side and visual styling (design tokens, colors, typography, spacing) on the other.
- **Design_Token**: A named, centrally defined visual value (such as a color, font size, or spacing unit) consumed by UI components.
- **Screen_Reader**: The Android TalkBack accessibility service.
- **Character_Accuracy**: The proportion of correctly recognized characters in Recognized_Text relative to ground-truth characters, measured as 1 minus the character error rate on a defined evaluation dataset.
- **Cell_Accuracy**: The proportion of correctly recognized Braille cells relative to ground-truth cells on a defined evaluation dataset.
- **Embossed_Evaluation_Set**: A fixed, version-controlled collection of labeled photographs of machine-embossed English Braille used to measure accuracy.
- **Handwritten_Evaluation_Set**: A fixed, version-controlled collection of labeled photographs of handwritten slate-and-stylus English Braille used to measure accuracy.
- **Reference_Device**: The defined baseline Android device and OS version against which performance and latency targets are measured.
- **MVP_Target**: The minimum acceptable accuracy threshold that must be satisfied for an initial release of the System.
- **Production_Target**: The final accuracy goal for the System, set higher than the corresponding MVP_Target.
- **Application_Package**: The distributable Android APK or Play Internal Testing build of the System.

## Requirements

### Requirement 1: Camera Capture, Torch, and Focus Control

**User Story:** As an Operator, I want the app to control the camera, torch, and focus optimally for raking-light capture, so that white-on-white embossed dots cast shadows the System can detect.

#### Acceptance Criteria

1. WHEN the Operator opens the scanning screen, THE Camera_Module SHALL start a live camera preview within 2 seconds on the Reference_Device.
2. WHEN the live camera preview starts, THE Camera_Module SHALL enable the Torch by default to create Raking_Light illumination.
3. THE Camera_Module SHALL provide an Operator control to toggle the Torch on and off.
4. WHILE the live camera preview is active, THE Camera_Module SHALL maintain focus on a document positioned between 5 and 25 centimeters from the lens.
5. WHEN the Operator initiates a capture, THE Camera_Module SHALL capture a still frame at the highest still resolution supported by the active camera.
6. IF the device does not have a controllable Torch, THEN THE System SHALL inform the Operator, as both on-screen text and spoken audio, that external low-angle lighting is required and SHALL continue to allow scanning.
7. IF the System cannot access the camera because permission was denied, THEN THE System SHALL display and speak a message stating that camera permission is required and SHALL provide a control to open the device permission settings.
8. WHILE Embossed_Mode is active, THE Camera_Module SHALL keep the Torch enabled to preserve Raking_Light shadow contrast unless the Operator turns the Torch off.
9. IF the Camera_Module cannot engage macro or close-range focus, THEN THE System SHALL inform the Operator, as both on-screen text and spoken audio, that focus at close range is unavailable and SHALL continue to allow scanning.
10. IF the System cannot access the camera for a reason other than denied permission, THEN THE System SHALL inform the Operator, as both on-screen text and spoken audio, that the camera is unavailable and SHALL provide a control to retry.
11. IF a capture fails, THEN THE System SHALL inform the Operator, as both on-screen text and spoken audio, that the capture could not be completed, SHALL preserve the live camera preview, and SHALL allow the Operator to retry the capture.

### Requirement 2: Live Camera Alignment Guidance

**User Story:** As an Operator who cannot read Braille by touch, I want spoken and visual guidance while aiming the camera, so that I can position the document correctly without sighted assistance.

#### Acceptance Criteria

1. WHILE the live camera preview is active, THE Alignment_Guide SHALL evaluate document distance, framing, steadiness, lighting, and flatness at least twice per second.
2. IF the document occupies less than 25 percent of the frame area, THEN THE Alignment_Guide SHALL instruct the Operator to move closer.
3. IF the document occupies more than 90 percent of the frame area, THEN THE Alignment_Guide SHALL instruct the Operator to move farther away.
4. IF the measured apparent movement exceeds 2 percent of the frame width per evaluation cycle, THEN THE Alignment_Guide SHALL instruct the Operator to hold steady.
5. IF the measured average frame luminance is below 20 percent on a normalized 0-to-100-percent scale, THEN THE Alignment_Guide SHALL instruct the Operator to turn on the Torch or add light.
6. IF the detected document plane deviates from parallel by more than 15 degrees, THEN THE Alignment_Guide SHALL instruct the Operator to flatten the document relative to the lens.
7. WHEN distance, framing, steadiness, lighting, and flatness have each been verified to meet their defined thresholds in the current evaluation cycle, THE Alignment_Guide SHALL announce that the document is ready to scan.
8. THE Alignment_Guide SHALL deliver each guidance instruction as both spoken audio and an on-screen visual indicator.
9. WHILE delivering guidance, THE Alignment_Guide SHALL present only one active instruction at a time, prioritizing the condition furthest from its defined threshold.
10. WHILE the ready-to-scan state is active, THE Alignment_Guide SHALL maintain the ready-to-scan state during threshold fluctuations shorter than the defined debounce period of 750 milliseconds.
11. IF one or more alignment conditions remain out of threshold for longer than the debounce period of 750 milliseconds, THEN THE Alignment_Guide SHALL leave the ready-to-scan state and resume active guidance.
12. IF the document leaves the frame while the ready-to-scan state is active, THEN THE Alignment_Guide SHALL leave the ready-to-scan state and resume active guidance within 500 milliseconds.
13. THE Alignment_Guide SHALL leave the ready-to-scan state before announcing readiness again.
14. IF no document is detected in the frame, THEN THE Alignment_Guide SHALL instruct the Operator to point the camera at a Braille document.
15. WHEN an alignment condition crosses a defined threshold, THE Alignment_Guide SHALL deliver the corresponding guidance change within 500 milliseconds.

### Requirement 3: Image Preprocessing

**User Story:** As an Operator, I want captured images corrected for perspective and uneven lighting, so that the detection model receives a clean, normalized image.

#### Acceptance Criteria

1. WHEN a still frame is captured, THE Image_Preprocessor SHALL detect a document boundary that encloses at least a defined minimum fraction of the frame area.
2. WHEN a document boundary is detected, THE Image_Preprocessor SHALL apply perspective correction to produce a rectified image in which the document edges are aligned to the image axes within a defined edge-alignment tolerance.
3. WHEN a frame is rectified, THE Image_Preprocessor SHALL apply lighting normalization so that the variation in illumination across the rectified image is reduced to within a defined illumination-uniformity threshold.
4. WHEN lighting normalization of a rectified frame completes, THE Image_Preprocessor SHALL pass the resulting preprocessed image to the Dot_Detector.
5. IF the Image_Preprocessor cannot detect a document boundary within the frame, THEN THE Image_Preprocessor SHALL apply lighting normalization to the unrectified frame, SHALL pass the normalized unrectified frame to the Dot_Detector, and SHALL record that perspective correction was skipped.
6. WHEN a still frame is captured, THE Image_Preprocessor SHALL complete all preprocessing of that frame and hand the result to the Dot_Detector, whether the frame was rectified or passed through unrectified, within 1500 milliseconds on the Reference_Device.

### Requirement 4: Machine-Learning Braille Dot Detection

**User Story:** As an Operator, I want a trained ML model to detect real raised dots under real camera conditions, so that recognition works on physical paper rather than only on ideal digital images.

#### Acceptance Criteria

1. THE Dot_Detector SHALL be a trained machine-learning object-detection model bundled inside the Application_Package.
2. THE Dot_Detector SHALL execute entirely on the device using a bundled TFLite or ONNX runtime.
3. WHEN the Image_Preprocessor provides a preprocessed image, THE Dot_Detector SHALL output the location of each Braille dot or cell whose detection confidence meets the defined minimum dot-detection confidence threshold, each with an associated Confidence_Score.
4. WHERE Handwritten_Mode is active, THE Dot_Detector SHALL apply the Handwritten_Mode detection parameter set, which accommodates irregular dot spacing and variable dot depth.
5. IF Braille-like spatial structure can be inferred from the detected dot candidates but no valid Braille cell can be formed from that structure, THEN THE System SHALL prompt the Operator to adjust alignment, lighting, or distance before requesting a full rescan.
6. IF no usable dot candidates are detected OR no Braille-like spatial structure can be inferred from the detected dot candidates, THEN THE System SHALL report to the Operator that no Braille was recognized and SHALL prompt for a rescan.
7. THE Dot_Detector SHALL output dot detections in pixel coordinates referenced to the preprocessed image so that the Cell_Segmenter can group them spatially.

### Requirement 5: Braille Cell Segmentation

**User Story:** As an Operator, I want detected dots grouped into 2-by-3 cells and ordered correctly, so that the text is read in the right sequence.

#### Acceptance Criteria

1. WHEN the Dot_Detector outputs detected dots, THE Cell_Segmenter SHALL assign each accepted dot candidate used for recognition to at most one Braille cell, where each Braille cell occupies a single 2-column by 3-row grid of six candidate dot positions, and SHALL exclude noise dot candidates that do not fit any cell.
2. WHEN cells are formed, THE Cell_Segmenter SHALL group cells whose vertical centers lie within one-half of the median cell height of each other into a common line.
3. WHEN cells are grouped into lines, THE Cell_Segmenter SHALL order cells left to right by horizontal position within each line and SHALL order lines top to bottom by vertical position.
4. WHEN the horizontal gap between two adjacent cells in a line exceeds the defined word-spacing threshold of 1.5 times the median intra-line cell-to-cell spacing, THE Cell_Segmenter SHALL insert a word boundary between those two cells.
5. IF detected dots within a region cannot be grouped into a valid 2-column by 3-row cell grid, THEN THE Cell_Segmenter SHALL assign that region a Confidence_Score below the defined cell-confidence threshold and SHALL continue segmenting the remaining regions.
6. IF the Dot_Detector outputs no detected dots, THEN THE Cell_Segmenter SHALL produce an empty ordered set of cells.

### Requirement 6: Braille Pattern Recognition

**User Story:** As an Operator, I want each cell mapped to its six-dot pattern, so that the patterns can be translated into English.

#### Acceptance Criteria

1. WHEN the Cell_Segmenter provides an ordered set of cells, THE Pattern_Recognizer SHALL map each cell to a six-dot Braille pattern identifying which of the six positions are raised.
2. THE Pattern_Recognizer SHALL assign a Confidence_Score to each recognized cell.
3. IF a cell's Confidence_Score is below the defined cell-confidence threshold, THEN THE Pattern_Recognizer SHALL flag that cell as uncertain in the output.

### Requirement 7: Braille-to-English Translation with Grade 1 and Grade 2 Support

**User Story:** As an Operator, I want recognized Braille translated into English supporting both Grade 1 and Grade 2 using liblouis, so that I can read uncontracted and contracted worksheets.

#### Acceptance Criteria

1. THE Translation_Engine SHALL be the liblouis library compiled to run on the device.
2. THE Translation_Engine SHALL support translation of Grade 1 English Braille and Grade 2 English Braille.
3. WHEN the Translation_Engine produces Recognized_Text, THE System SHALL display the Recognized_Text on screen.
4. THE Translation_Engine SHALL use only liblouis translation tables bundled within the Application_Package.
5. IF the Translation_Engine cannot translate one or more recognized cells, THEN THE System SHALL display the untranslatable cell patterns and SHALL inform the Operator that those cells could not be translated.

### Requirement 8: Grade Auto-Detection with Manual Override

**User Story:** As an Operator, I want automatic grade detection plus a one-tap manual override, so that I can read any worksheet even when automatic detection is unreliable.

#### Acceptance Criteria

1. WHERE Grade_Mode is set to Auto, THE Grade_Detector SHALL estimate whether the recognized patterns are Grade 1 Braille or Grade 2 Braille and SHALL select that grade for translation.
2. WHERE Grade_Mode is set to Auto, THE System SHALL display the grade that the Grade_Detector selected for the current scan.
3. THE System SHALL provide a one-tap Operator control to override Grade_Mode to Grade 1 or to Grade 2.
4. WHEN the Operator overrides Grade_Mode, THE Translation_Engine SHALL re-translate the current recognized patterns using the selected grade and THE System SHALL update the displayed Recognized_Text.
5. THE System SHALL display a statement that automatic grade detection is an estimate and may be incorrect, alongside the grade override control.
6. THE System SHALL default Grade_Mode to Auto when the scanning screen is first opened in a session.

### Requirement 9: Embossed and Handwritten Scanning Modes with Confidence Tiering

**User Story:** As an Operator, I want to choose between embossed and handwritten scanning modes with honest confidence labeling, so that the System optimizes detection and I know how much to trust handwritten results.

#### Acceptance Criteria

1. THE System SHALL provide an Operator control to select Embossed_Mode or Handwritten_Mode.
2. THE System SHALL default to Embossed_Mode when the scanning screen is first opened in a session.
3. WHEN the Operator selects a Scanning_Mode, THE System SHALL apply the detection and preprocessing parameters associated with that mode to subsequent scans.
4. THE System SHALL display the active Scanning_Mode on the scanning screen.
5. WHERE Handwritten_Mode is active, THE System SHALL label the Recognized_Text as a lower-confidence second tier whenever results are displayed.
6. WHERE Handwritten_Mode is active, THE System SHALL deliver, as spoken audio, a statement that handwritten results are less reliable than embossed results when the first handwritten scan of a session completes.
7. THE System SHALL provide an Operator control to disable the spoken handwritten-reliability statement for the remainder of the session after it has been delivered once.

### Requirement 10: Text Output Display

**User Story:** As an Operator, I want the recognized English displayed clearly with uncertain parts marked, so that I can read and verify the result.

#### Acceptance Criteria

1. WHEN Recognized_Text is produced, THE System SHALL display the Recognized_Text on the results area of the screen.
2. THE System SHALL display a Confidence_Score for each completed scan.
3. WHEN a recognized character has a Confidence_Score below the defined display-confidence threshold, THE System SHALL visually mark that character as uncertain within the displayed Recognized_Text.
4. THE System SHALL provide an Operator control to copy the Recognized_Text to the device clipboard.
5. THE System SHALL render the Recognized_Text using the Design_Tokens defined in the Theming_Layer.
6. IF the display is unavailable when Recognized_Text is produced, THEN THE System SHALL complete recognition and SHALL retain the Recognized_Text for presentation when the display becomes available.

### Requirement 11: Text-to-Speech Output

**User Story:** As an Operator, I want the recognized English read aloud offline, so that I can hear the content hands-free.

#### Acceptance Criteria

1. THE TTS_Engine SHALL use the Android built-in TextToSpeech facility and SHALL operate without a network connection.
2. WHEN Recognized_Text is produced, THE TTS_Engine SHALL read the Recognized_Text aloud.
3. THE System SHALL provide Operator controls to replay, pause, and stop the spoken Recognized_Text.
4. IF the Android built-in TextToSpeech facility is unavailable or inadequate, THEN THE System SHALL fall back to another available offline TTS engine.
5. IF no offline TTS voice data is installed on the device, THEN THE System SHALL display a message that a TTS voice must be installed, SHALL announce that message if any audio output mechanism is available, and SHALL provide a control to open the device TTS settings.
6. WHILE a TTS installation error is displayed, THE System SHALL allow any audio that is already playing to continue.
7. THE System SHALL use offline text-to-speech as the default speech output mechanism.
8. WHERE an internet connection is available and the Operator has enabled cloud features, THE System MAY use an approved cloud speech service to provide enhanced speech functionality, and that cloud speech functionality SHALL NOT be required for Braille recognition, text display, or baseline offline speech output.

### Requirement 12: Near Real-Time and Capture Output Modes

**User Story:** As an Operator, I want both a near-real-time live mode and a reliable scan-and-read capture mode, so that I can choose between speed and the most accurate result.

#### Acceptance Criteria

1. THE System SHALL provide a Live_Mode and a Capture_Mode and SHALL provide an Operator control to switch between them.
2. WHILE Live_Mode is active on the Reference_Device, THE System SHALL update Recognized_Text within 1000 milliseconds of acquiring a usable frame.
3. WHEN the Operator initiates a capture in Capture_Mode for machine-embossed Braille on the Reference_Device, THE System SHALL display Recognized_Text within 4 seconds of capture.
4. WHEN Recognized_Text is displayed, THE TTS_Engine SHALL begin reading it aloud within 1000 milliseconds.
5. WHILE the System is processing a capture, THE System SHALL display and announce a processing indicator.

### Requirement 13: Recognition Accuracy Targets and Measurement

**User Story:** As an Operator, I want honest, measurable accuracy with clearly defined evaluation sets, so that I know how much to trust the output for embossed versus handwritten Braille.

#### Acceptance Criteria

1. WHERE Embossed_Mode is active, THE System SHALL achieve the embossed Production_Target of simultaneously at least 95% Character_Accuracy and at least 98% Cell_Accuracy on the Embossed_Evaluation_Set.
2. WHERE Embossed_Mode is active, THE System SHALL achieve the embossed MVP_Target of simultaneously at least 85% Character_Accuracy and at least 90% Cell_Accuracy on the Embossed_Evaluation_Set.
3. THE System SHALL treat the embossed Production_Target as met only when both the Character_Accuracy threshold and the Cell_Accuracy threshold are satisfied on the Embossed_Evaluation_Set.
4. WHERE Handwritten_Mode is active, THE System SHALL achieve the handwritten Production_Target of at least 70% Character_Accuracy on the Handwritten_Evaluation_Set.
5. WHERE Handwritten_Mode is active, THE System SHALL achieve the handwritten MVP_Target of at least 50% Character_Accuracy on the Handwritten_Evaluation_Set.
6. THE System SHALL treat each MVP_Target as the minimum acceptable accuracy bar for release and each Production_Target as the final accuracy goal.
7. THE System SHALL measure Character_Accuracy as 1 minus the character error rate between Recognized_Text and the ground-truth text of each evaluation item.
8. THE Embossed_Evaluation_Set and the Handwritten_Evaluation_Set SHALL each be fixed and version-controlled so that accuracy measurements are repeatable.
9. THE System SHALL display a Confidence_Score for each completed scan as defined in Requirement 10.

### Requirement 14: Error and Low-Confidence Handling

**User Story:** As an Operator, I want clear guidance when a scan fails or confidence is low, so that I can recover without technical help.

#### Acceptance Criteria

1. IF a scan produces no recognizable Braille cells, THEN THE System SHALL inform the Operator that no Braille was recognized and SHALL offer to rescan.
2. IF the overall scan Confidence_Score is below the defined rescan-recommendation threshold, THEN THE System SHALL recommend that the Operator rescan and SHALL state the likely cause based on the failed alignment condition.
3. IF an unexpected processing error occurs during a scan, THEN THE System SHALL return to the scanning screen and SHALL inform the Operator that the scan could not be completed.
4. WHEN a scan-failure condition or a low-confidence condition occurs, THE System SHALL generate the corresponding failure or low-confidence message.
5. WHEN the System reports a scan failure or a low-confidence result, THE System SHALL deliver the message as both spoken audio and on-screen text.
6. IF one delivery method for a failure or low-confidence message is unavailable, THEN THE System SHALL still deliver the message through the available method.

### Requirement 15: On-Device, Offline, No-Backend Operation

**User Story:** As an Operator, I want the app to work any time without any server, so that it keeps working after the developer is no longer involved.

#### Acceptance Criteria

1. THE System SHALL perform all camera capture, preprocessing, dot detection, segmentation, recognition, translation, and speech synthesis on the device.
2. THE System SHALL complete a full scan-to-speech cycle while the device has no network connection.
3. THE System SHALL operate without contacting any backend server controlled by the developer.
4. THE System SHALL bundle the Dot_Detector model, the Translation_Engine tables, and all required runtimes within the Application_Package.
5. WHERE an optional cloud-boost recognition or cloud speech mode exists, THE System SHALL keep that mode disabled by default and SHALL remain fully functional for the complete scan-to-speech cycle while that mode is disabled.
6. WHERE an optional cloud mode is enabled by the Operator and network connectivity is available, THE System MAY contact approved third-party services, and THE System SHALL NOT contact any backend server controlled by the developer.

### Requirement 16: Privacy of Camera Data

**User Story:** As an Operator handling student work, I want camera frames to stay on the device, so that the privacy of students and clients is protected.

#### Acceptance Criteria

1. THE System SHALL process all camera frames only in device memory and SHALL retain camera frames on the device.
2. THE System SHALL persist a captured camera frame to device storage only when the Operator explicitly requests to save a scan.
3. WHERE an optional cloud-boost recognition or cloud speech mode is enabled by explicit Operator opt-in, THE System SHALL require that opt-in before any image data leaves the device.
4. WHERE an optional cloud mode is enabled, THE System SHALL delete local temporary copies of transmitted images after transmission completes, and SHALL use only cloud providers with documented data-retention controls compatible with the project's privacy requirements.
5. WHERE an optional cloud mode is available, THE System SHALL require institutional approval before cloud processing of saved scans is permitted.

### Requirement 17: Accessibility-First Interaction

**User Story:** As a non-technical Operator who may rely on assistive technology, I want a fully accessible interface, so that I can use every feature regardless of ability.

#### Acceptance Criteria

1. THE System SHALL be operable end to end using the Android Screen_Reader.
2. THE System SHALL assign a descriptive accessibility label to each interactive control.
3. THE System SHALL render primary action controls at a minimum touch-target size of 48 by 48 density-independent pixels.
4. THE System SHALL present text and essential UI elements at a contrast ratio of at least 4.5 to 1 against their background.
5. THE System SHALL provide spoken voice guidance for the primary scanning workflow.
6. WHEN the System changes processing state, THE System SHALL announce the new state through the Screen_Reader.
7. THE System SHALL provide a high-contrast visual theme for low-vision Operators.

### Requirement 18: Modern, Designer-Ready UI Architecture

**User Story:** As the project owner, I want a modern UI with structure separated from visual styling, so that a designer can apply a polished design layer later without rearchitecting.

#### Acceptance Criteria

1. THE System SHALL define all colors, typography, and spacing values as Design_Tokens within a centralized Theming_Layer.
2. THE System SHALL have UI components consume visual values exclusively through the Theming_Layer rather than through hard-coded values.
3. THE System SHALL require each Design_Token to be defined in the Theming_Layer before any component consumes that token.
4. WHEN a Design_Token value is changed, THE System SHALL apply the change to every component that consumes that token without modification to component structure.
5. THE System SHALL separate UI component structure and component contracts from visual styling definitions.

### Requirement 19: Installation and Offline Readiness

**User Story:** As an Installer who is non-technical, I want to install the app from an APK or Play Internal Testing link with everything bundled, so that I can set it up without developer assistance.

#### Acceptance Criteria

1. THE System SHALL be distributable as an installable Application_Package in APK form or via a Play Internal Testing link.
2. THE Application_Package SHALL include all models, translation tables, and runtimes required for offline operation so that no additional downloads are required after installation.
3. WHEN the Application_Package is installed on a supported Android device, THE System SHALL launch and reach the scanning screen without any developer-operated server being available.
4. THE System SHALL request the camera permission, and any other required runtime permissions, at first use with a plain-language explanation of why each permission is needed.
