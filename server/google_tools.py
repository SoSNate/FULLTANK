import os
import sys
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

SCOPES = [
    'https://www.googleapis.com/auth/spreadsheets',
    'https://www.googleapis.com/auth/drive.file'
]

def get_credentials():
    """Gets valid user credentials from token.json or runs the OAuth flow if needed."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    token_path = os.environ.get('GOOGLE_TOKEN_PATH', os.path.join(script_dir, 'token.json'))
    creds_path = os.environ.get('GOOGLE_CREDENTIALS_PATH', os.path.join(script_dir, 'credentials.json'))
    
    creds = None
    if os.path.exists(token_path):
        try:
            creds = Credentials.from_authorized_user_file(token_path, SCOPES)
        except Exception:
            creds = None

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            try:
                creds.refresh(Request())
            except Exception:
                creds = None
                
        if not creds:
            if not os.path.exists(creds_path):
                print(f"[Warning] No credentials.json found at {creds_path}. Google API functions may fail.")
                return None
            flow = InstalledAppFlow.from_client_secrets_file(creds_path, SCOPES)
            creds = flow.run_local_server(port=0)
            
        if creds:
            with open(token_path, 'w') as token_file:
                token_file.write(creds.to_json())
    return creds

def read_spreadsheet(file_id, sheet_name=None, range_name="A1:Z100"):
    """Reads rows and columns from Google Spreadsheet."""
    creds = get_credentials()
    if not creds:
        return []
    service = build('sheets', 'v4', credentials=creds)
    
    if not sheet_name:
        spreadsheet = service.spreadsheets().get(spreadsheetId=file_id).execute()
        sheets = spreadsheet.get('sheets', [])
        if not sheets:
            return []
        sheet_name = sheets[0]['properties']['title']
    
    target_range = f"'{sheet_name}'!{range_name}"
    result = service.spreadsheets().values().get(
        spreadsheetId=file_id,
        range=target_range
    ).execute()
    
    return result.get('values', [])
