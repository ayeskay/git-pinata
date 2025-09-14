#!/bin/bash
# Test Pinata authentication

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
    echo "Loaded credentials from .env"
else
    echo "Error: .env file not found"
    exit 1
fi

echo "Testing JWT authentication..."
JWT_RESPONSE=$(curl -s -X GET "https://api.pinata.cloud/data/testAuthentication" \
     -H "Authorization: Bearer $PINATA_JWT")

if echo "$JWT_RESPONSE" | grep -q "Congratulations"; then
    echo "✅ JWT Authentication successful!"
    echo "Response: $JWT_RESPONSE"
else
    echo "❌ JWT Authentication failed"
    echo "Response: $JWT_RESPONSE"
fi

echo ""
echo "Testing API Key/Secret authentication..."
API_RESPONSE=$(curl -s -X GET "https://api.pinata.cloud/data/testAuthentication" \
     -H "pinata_api_key: $PINATA_API_KEY" \
     -H "pinata_secret_api_key: $PINATA_SECRET_API_KEY")

if echo "$API_RESPONSE" | grep -q "Congratulations"; then
    echo "✅ API Key/Secret Authentication successful!"
    echo "Response: $API_RESPONSE"
else
    echo "❌ API Key/Secret Authentication failed"
    echo "Response: $API_RESPONSE"
fi
