import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from flask import Flask, request, Response, stream_with_context
from PIL import Image
import io
import base64
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load the model and tokenizer
model_name = "openbmb/MiniCPM-Llama3-V-2_5-int4"
tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True, padding_side="left")

# Check CUDA availability
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
logger.info(f"Using device: {device}")

# Load the model with optimizations
model = AutoModelForCausalLM.from_pretrained(
    model_name,
    trust_remote_code=True,
    torch_dtype=torch.float16,
    device_map="auto",
    low_cpu_mem_usage=True
)
model.eval()

# Ensure pad token consistency
model.config.pad_token_id = tokenizer.pad_token_id

@app.route('/', methods=['POST'])
def receive_message():
    try:
        data = request.json
        logger.info(f"Received data: {data}")

        message = data.get("message", "")
        image_base64 = data.get("image", "")

        if not image_base64:
            logger.warning("No image provided")
            return Response("No image provided", status=400)

        try:
            image_data = base64.b64decode(image_base64)
            image = Image.open(io.BytesIO(image_data)).convert('RGB')
        except Exception as e:
            logger.error(f"Failed to decode image: {e}")
            return Response("Invalid image data", status=400)

        context = ("""You are an AI assistant specialized in analyzing screenshots from Minecraft. 
        You can see and understand these images. Always assume the screenshots are from this game. 
        Answer questions about the images.""")
        msgs = [
            {'role': 'user',
             'content': f"{context}\n\nHere is the screenshot from Minecraft along with a message: {message}"}
        ]

        def generate():
            with torch.inference_mode():
                for new_text in model.chat(
                    image=image,
                    msgs=msgs,
                    tokenizer=tokenizer,
                    max_gen_len=256,
                    temperature=0.7,
                    top_p=0.95,
                    stream=True
                ):
                    yield new_text + "\n"  # Add newline for better client-side handling

        return Response(stream_with_context(generate()), content_type='text/plain')

    except Exception as e:
        logger.error(f"An error occurred: {str(e)}", exc_info=True)
        return Response(f"An error occurred: {str(e)}", status=500)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)