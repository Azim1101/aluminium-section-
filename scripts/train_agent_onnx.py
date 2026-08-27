import json
import math
import re
import numpy as np
import onnx
from onnx import helper, TensorProto
import onnxruntime as ort

INTENTS = [
    "GREETINGS",
    "HOW_ARE_YOU",
    "WHO_ARE_YOU",
    "APP_INFO",
    "WINDOW_TYPES",
    "CALCULATION",
    "SUTTER",
    "MULIYA",
    "PIPE_CUTTING",
    "PRICE",
    "CUSTOMER",
    "USER_PROFILE",
    "BACKUP",
    "EXPORT",
    "SETTINGS",
    "MANUAL_PCO",
    "THANKS",
    "GOODBYE",
    "ZED",
    "DOMAL",
    "HELP",
    "UNITS",
    "WASTE",
    "FORMULA"
]

intent_to_id = {name: i for i, name in enumerate(INTENTS)}

DATA = {
    "GREETINGS": [
        "hi", "hello", "hey", "namaste", "namaskar", "hlo", "hii", "hiii",
        "good morning", "good evening", "good afternoon", "pranam", "ram ram", "radhe radhe",
        "sasriyakaal", "salam", "adaab", "jai shree krishna", "hello sir", "hi alu", "hello alu agent",
        "hey there", "namaste ji", "hlo sir", "hi bhai", "hello bhai", "hey bro", "kya haal chaal", "namaskaram"
    ],
    "HOW_ARE_YOU": [
        "how are you", "kaise ho", "kaisa hai", "kaise ho bhai", "kya haal", "kya haal hai",
        "kaise ho aap", "sab kaisa hai", "sab theek", "how do you do", "are you okay",
        "kya chal raha hai", "kaisa chal rha hai", "kaise chal raha", "all good", "how are you doing",
        "bhai kaisa hai", "aap kaise hain", "kya haal chal", "kaisa chal raha hai kaam", "theek ho na", "tum kaise ho", "tum kaise ho mere dost", "tum kaisa hai"
    ],
    "WHO_ARE_YOU": [
        "who are you", "tum kaun", "kaun ho", "your name", "tera naam", "apna naam",
        "what is your name", "aap kaun ho", "tumhara naam kya hai", "tell me about yourself",
        "introduce yourself", "apna parichay do", "who created you", "who made you",
        "identity", "kya ho tum", "tumhe kisne banaya", "agent name", "assistant kaun hai", "tum kon ho"
    ],
    "APP_INFO": [
        "app kya hai", "app ke baare", "about app", "what is this app", "ye app", "ye kya hai",
        "app ka naam", "alu window kya hai", "ye software kiske liye hai", "app features",
        "application info", "is app me kya kya hai", "software details", "application ke bare me batao",
        "ye kis kaam aata hai", "app kaise use kare", "aluminium app info", "app overview", "app kisne banayi", "app ke bare me"
    ],
    "WINDOW_TYPES": [
        "window types", "window ka type", "kitne type", "types of window", "window system",
        "system types", "sliding window types", "types of section", "khidki ke types",
        "window systems available", "window variety", "kitni tarah ki khidki", "sliding windows",
        "double track single track windows", "kaun kaun se window system hain", "window designs", "different windows"
    ],
    "CALCULATION": [
        "calculation", "calculate", "hisab", "calc kaise", "calculation kaise", "kaise calculate",
        "measurement", "naap kaise le", "window measurement", "cut calculation", "size calculation",
        "window hisab", "calculation formula", "naap se cut size nikalna", "khidki ka naap kaise calculate kare",
        "height width calculation", "size entry kaise kare", "dimension calculation", "cutting size nikalo",
        "naap nikalne ka hisab", "calculation batao", "window sizing", "size hisab"
    ],
    "SUTTER": [
        "sutter", "shutter", "sutter kya", "sutter calculation", "shutter size",
        "shutter height", "shutter width", "sutter naap", "shutter kaise nikalte hain",
        "shutter overlap", "sutter deduction", "sliding panel size", "shutter formula",
        "sutter ki lambai", "sutter ki chaudai", "sutter cut length", "shutter dimension",
        "shutter panel", "sutter clear", "sutter ki unchai"
    ],
    "MULIYA": [
        "muliya", "muliya kya", "interlock", "muliya calculation", "short muliya",
        "long muliya", "t muliya", "muliya quantity", "kitne muliya chahiye",
        "interlock section", "muliya formula", "muliya height", "muliya cut size",
        "muliya kaise lagate hain", "interlock calculation", "muliya spacing", "interlock profile", "muliya tukde"
    ],
    "PIPE_CUTTING": [
        "pipe cutting", "pipe cut", "cutting plan", "cutting details", "pipe kaise kate",
        "pipe cutting plan", "stock pipe cut", "cut list", "pipe optimization", "pipe layout",
        "cutting list", "bin packing pipe", "pipe katne ka tarika", "pipe bar diagram",
        "aluminium pipe cut", "how to cut pipe", "optimal pipe cutting", "bar cutting", "pipe cut sizes",
        "pipe katna hai", "pipe cuts", "pipe cutting me bachat", "pipe cutting kaise kare", "pipe cutting optimization", "pipe cutting details", "pipe cutting list"
    ],
    "PRICE": [
        "price", "daam", "rate", "ke rate", "price kaise", "price setup", "cost",
        "rate list", "price book", "costing", "aluminium rate per kg", "glass cost",
        "labour charge", "profit margin", "rate kaise set kare", "price change kaise kare",
        "total cost estimate", "estimate price", "rupaye", "kharcha", "daam kaise badle", "rate setup",
        "price list update", "rate change pin", "daam set karna"
    ],
    "CUSTOMER": [
        "customer", "customer record", "save customer", "customer kaise", "customer data",
        "customer list", "client record", "customer save kaise kare", "customer view",
        "grahak ka hisab", "customer search", "save estimate customer", "customer phone village",
        "saved customers kahan hai", "customer data load kaise kare", "customer database",
        "naya customer add karna", "grahak details", "customer record open"
    ],
    "USER_PROFILE": [
        "my profile", "user profile", "mera profile", "profile kya", "owner profile",
        "apna profile", "profile update", "dukaan ka naam", "shop name", "business details",
        "company info", "mera naam mobile update", "profile kaise change kare", "profile setup",
        "fabricator profile", "workshop details", "business header", "dukan ka address", "profile edit"
    ],
    "BACKUP": [
        "backup", "backup kaise", "data save", "data backup", "data kahan", "restore",
        "restore kaise kare", "data restore", "backup restore", "json backup file",
        "phone badalne par data", "backup download", "data safe kaise kare", "backup file kahan save hoti hai",
        "export backup", "import backup", "save data offline", "phone format backup"
    ],
    "EXPORT": [
        "export", "excel", "xlsx", "export kaise", "excel export", "share on whatsapp",
        "whatsapp share", "export estimate", "excel sheet banaye", "share cutting images",
        "file export", "quotation share", "estimate send kare", "excel file download",
        "whatsapp pe bill kaise bheje", "share plan", "whatsapp estimate", "send file", "excel me export", "excel me export kaise kare", "excel kaise nikale", "excel file export", "excel sheet export", "excel me save", "export excel"
    ],
    "SETTINGS": [
        "settings", "setting", "configuration", "setup", "settings kya", "settings kaise",
        "preferences", "deduction setting", "stock length setting", "app configuration",
        "setting me kya hai", "settings open kaise kare", "pin change setting", "gear icon",
        "settings menu", "preferences configure"
    ],
    "MANUAL_PCO": [
        "manual pco", "pco", "manual cutting", "sheet cutting", "manual", "manual kaise",
        "manual pipe cutting", "sheet layout", "face cutting", "sheet nesting", "maxrects",
        "manual cut sizes", "custom cutting sheet", "scrap nesting", "sheet pieces",
        "sheet cutting optimize", "manual sheet", "sheet ka naap"
    ],
    "THANKS": [
        "thanks", "thank you", "shukriya", "dhanyavad", "thank", "bahot badhiya",
        "great help", "thank you so much", "bhai thanks", "bohot achha", "shukriya dost",
        "dhanyawad", "thank u", "many thanks", "nice work", "superb", "bohot badhiya bhai"
    ],
    "GOODBYE": [
        "bye", "goodbye", "alvida", "tata", "bye bye", "chalta hoon", "phir milenge",
        "see you", "take care", "shubh ratri", "good night", "alvida dosto", "bye brother", "chalta hu"
    ],
    "ZED": [
        "zed", "zed kya hai", "zed system", "zed section", "zed single track",
        "zed double track", "zed sliding", "zed dimensions", "zed pipe length",
        "zed sutter", "zed frame", "zed profile", "zed window system", "zed sliding window", "zed 252"
    ],
    "DOMAL": [
        "domal", "domal kya", "domal system", "domal section", "domal double track",
        "domal sliding", "domal heavy section", "domal profile", "domal track",
        "domal window details", "domal rates", "domal cut calculation", "domal series"
    ],
    "HELP": [
        "help", "madad", "help karo", "madad karo", "kya kar sakte ho", "help me",
        "agent help", "mujhe samajh nahi aa raha", "guide karo", "kya kya pooch sakte hain",
        "sawaal kaise pooche", "options dikhao", "help command", "show menu", "kya madad kar sakte ho", "help list"
    ],
    "UNITS": [
        "unit", "inch", "mm", "millimeter", "inch vs mm", "unit kya", "unit change",
        "inch to millimeter", "mm me measurement", "inch me naap", "unit conversion",
        "unit kaise badle", "measurement unit", "inch ko millimeter me"
    ],
    "WASTE": [
        "waste", "waste kya", "kitna waste", "waste percentage", "scrap",
        "bachat", "pipe bachat", "scrap material", "waste minimum kaise kare",
        "aluminium waste", "waste calculation", "kam se kam waste", "scrap reduce", "scrap kam kaise kare", "bachat kitna hoga", "pipe ki bachat", "kitna waste bachega", "waste aur bachat", "scrap material bachat"
    ],
    "FORMULA": [
        "formula", "custom formula", "apna formula", "formula kaise", "formula kya",
        "custom system formula", "math evaluator", "formula add kare", "formula rules",
        "expression formula", "apna calculation formula kaise banaye", "formula variables", "khud ka formula"
    ]
}

def clean_text(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s]', ' ', text)
    return text.strip()

all_tokens = set()
for intent, examples in DATA.items():
    for text in examples:
        cleaned = clean_text(text)
        words = cleaned.split()
        for w in words:
            all_tokens.add(w)
            if len(w) >= 3:
                for i in range(len(w) - 2):
                    all_tokens.add(w[i:i+3])

vocab_list = sorted(list(all_tokens))
VOCAB_SIZE = min(len(vocab_list), 1024)
vocab = {tok: idx for idx, tok in enumerate(vocab_list[:VOCAB_SIZE])}
print(f"Vocab size: {VOCAB_SIZE}")

def text_to_vector(text):
    vec = np.zeros(VOCAB_SIZE, dtype=np.float32)
    cleaned = clean_text(text)
    words = cleaned.split()
    for w in words:
        if w in vocab:
            vec[vocab[w]] += 2.0  # Whole words get higher weight
        if len(w) >= 3:
            for i in range(len(w) - 2):
                tri = w[i:i+3]
                if tri in vocab:
                    vec[vocab[tri]] += 0.5
    norm = np.linalg.norm(vec)
    if norm > 0:
        vec /= norm
    return vec

# Prepare dataset with augmentation
X_list = []
y_list = []

for intent, examples in DATA.items():
    label_id = intent_to_id[intent]
    for text in examples:
        X_list.append(text_to_vector(text))
        y_list.append(label_id)
        
        words = clean_text(text).split()
        if len(words) > 2:
            X_list.append(text_to_vector(" ".join(words[:2])))
            y_list.append(label_id)
            X_list.append(text_to_vector(" ".join(words[1:])))
            y_list.append(label_id)
        if len(words) >= 4:
            X_list.append(text_to_vector(words[0] + " " + words[-1]))
            y_list.append(label_id)

X = np.array(X_list, dtype=np.float32)
y = np.array(y_list, dtype=np.int64)
N = X.shape[0]
num_classes = len(INTENTS)
print(f"Total training samples: {N}, Classes: {num_classes}")

np.random.seed(42)
hidden1_dim = 64
hidden2_dim = 32

W1 = (np.random.randn(VOCAB_SIZE, hidden1_dim) * np.sqrt(2.0 / VOCAB_SIZE)).astype(np.float32)
b1 = np.zeros(hidden1_dim, dtype=np.float32)

W2 = (np.random.randn(hidden1_dim, hidden2_dim) * np.sqrt(2.0 / hidden1_dim)).astype(np.float32)
b2 = np.zeros(hidden2_dim, dtype=np.float32)

W3 = (np.random.randn(hidden2_dim, num_classes) * np.sqrt(2.0 / hidden2_dim)).astype(np.float32)
b3 = np.zeros(num_classes, dtype=np.float32)

lr = 0.005
epochs = 400

mW1, vW1 = np.zeros_like(W1), np.zeros_like(W1)
mb1, vb1 = np.zeros_like(b1), np.zeros_like(b1)
mW2, vW2 = np.zeros_like(W2), np.zeros_like(W2)
mb2, vb2 = np.zeros_like(b2), np.zeros_like(b2)
mW3, vW3 = np.zeros_like(W3), np.zeros_like(W3)
mb3, vb3 = np.zeros_like(b3), np.zeros_like(b3)

beta1, beta2, eps = 0.9, 0.999, 1e-8

for epoch in range(epochs):
    z1 = X @ W1 + b1
    a1 = np.maximum(0, z1)
    
    z2 = a1 @ W2 + b2
    a2 = np.maximum(0, z2)
    
    logits = a2 @ W3 + b3
    exp_logits = np.exp(logits - np.max(logits, axis=1, keepdims=True))
    probs = exp_logits / np.sum(exp_logits, axis=1, keepdims=True)
    
    correct_logprobs = -np.log(probs[np.arange(N), y] + 1e-10)
    loss = np.mean(correct_logprobs) + 1e-4 * (np.sum(W1**2) + np.sum(W2**2) + np.sum(W3**2))
    
    dlogits = probs.copy()
    dlogits[np.arange(N), y] -= 1.0
    dlogits /= N
    
    dW3 = a2.T @ dlogits + 2e-4 * W3
    db3 = np.sum(dlogits, axis=0)
    
    da2 = dlogits @ W3.T
    dz2 = da2 * (z2 > 0)
    dW2 = a1.T @ dz2 + 2e-4 * W2
    db2 = np.sum(dz2, axis=0)
    
    da1 = dz2 @ W2.T
    dz1 = da1 * (z1 > 0)
    dW1 = X.T @ dz1 + 2e-4 * W1
    db1 = np.sum(dz1, axis=0)
    
    t = epoch + 1
    for param, grad, m, v in [
        (W1, dW1, mW1, vW1), (b1, db1, mb1, vb1),
        (W2, dW2, mW2, vW2), (b2, db2, mb2, vb2),
        (W3, dW3, mW3, vW3), (b3, db3, mb3, vb3)
    ]:
        m[:] = beta1 * m + (1 - beta1) * grad
        v[:] = beta2 * v + (1 - beta2) * (grad ** 2)
        m_hat = m / (1 - beta1 ** t)
        v_hat = v / (1 - beta2 ** t)
        param -= lr * m_hat / (np.sqrt(v_hat) + eps)

preds = np.argmax(probs, axis=1)
acc = np.mean(preds == y) * 100
print(f"Training Accuracy: {acc:.2f}%")

test_queries = [
    ("hello alu window agent", "GREETINGS"),
    ("tum kaise ho mere dost", "HOW_ARE_YOU"),
    ("aapka naam bataiye", "WHO_ARE_YOU"),
    ("ye app kiske liye banaya gaya h", "APP_INFO"),
    ("kitni tarah ki khidki bana sakte h", "WINDOW_TYPES"),
    ("naap nikalne ka hisab batao", "CALCULATION"),
    ("shutter ki lambai kaise napte hain", "SUTTER"),
    ("interlock ka cut piece kitna hoga", "MULIYA"),
    ("pipe katne ka hisab kitna hoga", "PIPE_CUTTING"),
    ("rate setup karna hai daam kitna lagega", "PRICE"),
    ("naya customer add karna h", "CUSTOMER"),
    ("dukan ka address aur profile change karna", "USER_PROFILE"),
    ("phone format hone se pehle backup lena hai", "BACKUP"),
    ("whatsapp pe estimate bhejna h", "EXPORT"),
    ("settings me unit badalna hai", "SETTINGS"),
    ("sheet cutting optimize kaise kare", "MANUAL_PCO"),
    ("bohot bohot dhanyawad bhai", "THANKS"),
    ("achha chalta hu alvida", "GOODBYE"),
    ("zed sliding window", "ZED"),
    ("domal double track system details", "DOMAL"),
    ("kya madad kar sakte ho", "HELP"),
    ("inch ko millimeter me kaise convert kare", "UNITS"),
    ("scrap kam kaise kare pipe bachat", "WASTE"),
    ("apna khud ka formula add karna h", "FORMULA")
]

correct = 0
for q, expected in test_queries:
    v = text_to_vector(q).reshape(1, -1)
    z1 = np.maximum(0, v @ W1 + b1)
    z2 = np.maximum(0, z1 @ W2 + b2)
    l = z2 @ W3 + b3
    p = np.exp(l - np.max(l))
    p = p / np.sum(p)
    pred_id = np.argmax(p)
    pred_intent = INTENTS[pred_id]
    confidence = p[0, pred_id]
    is_corr = pred_intent == expected
    if is_corr: correct += 1
    print(f"Query: '{q}' -> Predicted: {pred_intent} ({confidence*100:.1f}%) [{'PASS' if is_corr else 'FAIL'}]")

print(f"Test Accuracy: {correct}/{len(test_queries)} ({correct/len(test_queries)*100:.1f}%)")

# Export to ONNX
input_info = helper.make_tensor_value_info('features', TensorProto.FLOAT, [1, VOCAB_SIZE])
output_info = helper.make_tensor_value_info('probabilities', TensorProto.FLOAT, [1, num_classes])

t_w1 = helper.make_tensor('W1', TensorProto.FLOAT, [VOCAB_SIZE, hidden1_dim], W1.flatten())
t_b1 = helper.make_tensor('b1', TensorProto.FLOAT, [hidden1_dim], b1.flatten())
t_w2 = helper.make_tensor('W2', TensorProto.FLOAT, [hidden1_dim, hidden2_dim], W2.flatten())
t_b2 = helper.make_tensor('b2', TensorProto.FLOAT, [hidden2_dim], b2.flatten())
t_w3 = helper.make_tensor('W3', TensorProto.FLOAT, [hidden2_dim, num_classes], W3.flatten())
t_b3 = helper.make_tensor('b3', TensorProto.FLOAT, [num_classes], b3.flatten())

node_gemm1 = helper.make_node('Gemm', ['features', 'W1', 'b1'], ['z1'], alpha=1.0, beta=1.0)
node_relu1 = helper.make_node('Relu', ['z1'], ['a1'])
node_gemm2 = helper.make_node('Gemm', ['a1', 'W2', 'b2'], ['z2'], alpha=1.0, beta=1.0)
node_relu2 = helper.make_node('Relu', ['z2'], ['a2'])
node_gemm3 = helper.make_node('Gemm', ['a2', 'W3', 'b3'], ['logits'], alpha=1.0, beta=1.0)
node_softmax = helper.make_node('Softmax', ['logits'], ['probabilities'], axis=1)

graph = helper.make_graph(
    [node_gemm1, node_relu1, node_gemm2, node_relu2, node_gemm3, node_softmax],
    'AgentIntentClassifier',
    [input_info],
    [output_info],
    [t_w1, t_b1, t_w2, t_b2, t_w3, t_b3]
)

onnx_model = helper.make_model(graph, producer_name='ALU_Window_Optimizer', opset_imports=[helper.make_opsetid('', 13)])
onnx.checker.check_model(onnx_model)

import os
output_dir = "app/src/main/assets" if os.path.exists("app/src/main/assets") else "/tmp"
model_path = os.path.join(output_dir, "agent_model.onnx")
vocab_path = os.path.join(output_dir, "agent_vocab.json")

with open(model_path, "wb") as f:
    f.write(onnx_model.SerializeToString())

# Save vocab and metadata JSON
vocab_data = {
    "vocab_size": VOCAB_SIZE,
    "tokens": vocab,
    "intents": INTENTS
}
with open(vocab_path, "w") as f:
    json.dump(vocab_data, f)

print(f"ONNX Model saved to {model_path}, size: {len(onnx_model.SerializeToString())/1024:.2f} KB")
print(f"Vocab saved to {vocab_path}")
