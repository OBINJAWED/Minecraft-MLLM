# Minecraft Multimodal LLM Integration

This project integrates a Multimodal Large Language Model (MLLM) with Minecraft, allowing players to capture screenshots and receive AI-generated responses based on the game environment.

![Video Preview](https://github.com/OBINJAWED/Minecraft-MLLM/blob/master/Minecraft-video-1.gif)

## Features

- Capture in-game screenshots with a custom command
- Send screenshots and messages to an AI server
- Receive AI-generated responses in the Minecraft chat
- Streaming responses for a smooth user experience (Note: There's a known bug in the streaming response)

## Components

### 1. Minecraft Mod (Fabric)

The Minecraft mod is responsible for:
- Capturing screenshots
- Sending messages and screenshots to the AI server
- Displaying AI responses in the game chat
- You can follow this tutorial to get set up: https://www.youtube.com/watch?v=RSqSZoJQXvg

Key features:
- Custom `/send` command to initiate interactions
- Asynchronous screenshot capture and processing
- Streaming response display in chat

### 2. Server (Flask)

The server handles:
- Receiving messages and screenshots from the Minecraft mod
- Processing images and text using a fine-tuned multimodal LLM
- Generating and streaming responses back to the game

Key features:
- Uses the MiniCPM-Llama3-V-2_5-int4 model (can be tweaked to use any multimodal LLM) - https://huggingface.co/openbmb/MiniCPM-Llama3-V-2_5-int4
- Image and text processing capabilities
- Streamed response generation

## Setup

### Requirements

- Java version of Minecraft
- Java 17 or 21 (must be compatible with your chosen version of the Fabric mod)
- Python 3.7+
- CUDA-capable GPU (for optimal performance)

### Minecraft Mod

1. Install the Fabric mod loader for Minecraft
2. Place the mod JAR file in your Minecraft mods folder
3. Launch Minecraft with the Fabric profile

### AI Server

1. Create and activate a virtual environment:
   ```
   python -m venv venv
   source venv/bin/activate  # On Windows, use `venv\Scripts\activate`
   ```
2. Install the required Python packages:
   ```
   pip install -r requirements.txt
   ```
3. Run the Flask server:
   ```
   python app.py
   ```

## Usage

1. In Minecraft, use the `/send` command followed by your message:
   ```
   /send What is this thing in front of me?
   ```
2. The mod will capture a screenshot and send it along with your message to the AI server
3. The AI-generated response will appear in your Minecraft chat, streamed word by word

Note: There's a known bug in the streaming response. While it works, it may not display correctly in all situations.

## How Screenshots Are Sent

1. When the `/send` command is used, the mod triggers a screenshot capture
2. The screenshot is saved to the local Minecraft screenshots folder
3. The mod reads the saved screenshot file and encodes it as a base64 string
4. The base64-encoded image is sent along with the user's message to the AI server

## Configuration

- Adjust the `SERVER_URL` in the Minecraft mod if your AI server is not running on the default `http://127.0.0.1:5000/`
- Modify the `model_name` in the AI server script if you want to use a different multimodal LLM

## Performance Considerations
This project was initially developed on Windows without access to optimizations like Flash Attention.

These optimizations can potentially speed up the process and improve the overall responsiveness of the AI integration.

## Notes

Ensure both the Minecraft client and AI server are running on the same machine or network
The AI server requires a CUDA-capable GPU for optimal performance
There may be a better approach to capturing and saving the screenshot but sending to the server. 

## Known Issues

There's a bug in the streaming response display. Multiple instances of the streamed responses are printed in the ide. 

## Contributing
Contributions are welcome! Please feel free to submit a Pull Request.

## License
This project is open-source and available under the MIT License.
