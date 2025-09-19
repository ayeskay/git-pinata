# pinata_client.py
import requests
import os
from dotenv import load_dotenv

load_dotenv()

class PinataClient:
    def __init__(self):
        self.api_key = os.getenv('PINATA_API_KEY')
        self.secret_key = os.getenv('PINATA_SECRET_API_KEY')
        self.base_url = "https://api.pinata.cloud"
    
    def get_file(self, cid):
        """Get file from IPFS via Pinata gateway using API key/secret"""
        url = f"https://gateway.pinata.cloud/ipfs/{cid}"
        
        # For public files, no auth needed
        # But for private pins, use API key authentication
        headers = {
            "pinata_api_key": self.api_key,
            "pinata_secret_api_key": self.secret_key
        }
        
        response = requests.get(url, headers=headers)
        
        # If auth fails, try without auth (for public files)
        if response.status_code == 401 or response.status_code == 403:
            response = requests.get(url)
        
        response.raise_for_status()
        return response.content
    
    def list_git_repos(self):
        """List Git repositories using Pinata API"""
        try:
            headers = {
                "pinata_api_key": self.api_key,
                "pinata_secret_api_key": self.secret_key
            }
            
            response = requests.get(
                f"{self.base_url}/data/pinList?metadata[keyvalues][repo]={{\"value\":\"*\",\"op\":\"ne\"}}",
                headers=headers
            )
            response.raise_for_status()
            pins = response.json().get('rows', [])
            return [{'cid': pin['ipfs_pin_hash'], 
                    'name': pin['metadata'].get('name', f"Repo {pin['ipfs_pin_hash'][:8]}..."),
                    'date': pin['date_pinned']} for pin in pins]
        except:
            # Fallback for demo
            return []
