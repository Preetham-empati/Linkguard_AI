from flask import Flask, request, jsonify
import re
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.feature_extraction.text import TfidfVectorizer

app = Flask(__name__)

# --- ML MODEL SETUP ---

# Sample training data (In a real app, this would be a large dataset like OpenPhish or PhishTank)
training_urls = [
    # Safe Links
    "google.com", "github.com", "stackoverflow.com", "amazon.com", "apple.com",
    "microsoft.com", "linkedin.com", "netflix.com", "wikipedia.org", "android.com",
    "nytimes.com", "bbc.com", "cnn.com", "medium.com", "twitter.com",
    # Malicious/Phishing Links
    "login-update-security.com", "secure-bank-verify.net", "verify-paypal-account.com",
    "account-locked-update.top", "bit.ly/fake-login-123", "update-your-password.xyz",
    "my-wallet-auth.io", "claim-reward-now.online", "signin.ebay.com-security.tk",
    "bankofamerica-login.verification-check.com", "official-support-help.info"
]

# Labels: 1 for Safe, 0 for Phishing
labels = [1] * 15 + [0] * 11

# Feature Extraction using TF-IDF on characters (effective for catching patterns in URLs)
vectorizer = TfidfVectorizer(analyzer='char', ngram_range=(3, 5))
X = vectorizer.fit_transform(training_urls)

# Train a simple Logistic Regression model
model = LogisticRegression()
model.fit(X, labels)

def extract_manual_features(url):
    """Additional heuristic checks to boost the ML decision."""
    features = {
        "length": len(url),
        "dots": url.count('.'),
        "has_at": 1 if '@' in url else 0,
        "has_ip": 1 if re.search(r'\d+\.\d+\.\d+\.\d+', url) else 0,
        "hyphens": url.count('-'),
        "is_shortened": 1 if any(s in url for s in ["bit.ly", "t.co", "goo.gl", "tinyurl"]) else 0
    }
    return features

# --- ENDPOINTS ---

@app.route('/verify', methods=['POST'])
def verify_link():
    data = request.json
    url = data.get("url", "").lower()

    if not url:
        return jsonify({"isSafe": True, "message": "Empty URL"})

    # 1. Use the ML Model for prediction
    url_vec = vectorizer.transform([url])
    probability = model.predict_proba(url_vec)[0]  # [Prob_Phishing, Prob_Safe]

    is_safe_ml = model.predict(url_vec)[0] == 1
    safe_score = probability[1] * 100

    # 2. Use Manual Heuristics (Rule-based boost)
    heuristics = extract_manual_features(url)

    is_suspicious = False
    reasons = []

    if heuristics["has_ip"]:
        is_suspicious = True
        reasons.append("Contains an IP address instead of a domain name.")
    if heuristics["hyphens"] > 3:
        is_suspicious = True
        reasons.append("Contains an unusual number of hyphens (common in phishing).")
    if heuristics["dots"] > 4:
        is_suspicious = True
        reasons.append("Contains too many subdomains.")

    # Combined Decision Logic
    # If ML is very sure it's phishing, or heuristics find major red flags
    final_is_safe = is_safe_ml and not (is_suspicious and safe_score < 80)

    if final_is_safe:
        message = f"TrustSphere analysis: This link appears safe (Confidence: {safe_score:.1f}%)."
    else:
        if reasons:
            message = "DANGER: " + " ".join(reasons)
        else:
            message = "WARNING: Our ML model detected high-risk patterns similar to known phishing sites."

    return jsonify({
        "isSafe": bool(final_is_safe),
        "message": message,
        "score": round(safe_score, 2)
    })

if __name__ == '__main__':
    print("TrustSphere ML Model trained and starting...")
    app.run(host='0.0.0.0', port=5000)
