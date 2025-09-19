# git_bundle_parser.py
import subprocess
import tempfile
import os
import shutil

class GitBundleParser:
    def __init__(self, bundle_path):
        self.bundle_path = bundle_path
        self.temp_repo = None
    
    def _setup_temp_repo(self):
        """Extract bundle to temporary Git repo"""
        if self.temp_repo and os.path.exists(self.temp_repo):
            return self.temp_repo
            
        self.temp_repo = tempfile.mkdtemp()
        try:
            # Clone bundle to temporary directory
            result = subprocess.run([
                'git', 'clone', self.bundle_path, self.temp_repo
            ], capture_output=True, text=True)
            
            if result.returncode != 0:
                raise Exception(f"Failed to parse Git bundle: {result.stderr}")
            return self.temp_repo
        except Exception as e:
            if self.temp_repo and os.path.exists(self.temp_repo):
                shutil.rmtree(self.temp_repo, ignore_errors=True)
            raise e
    
    def get_file_tree(self):
        """Get file tree structure"""
        repo_path = self._setup_temp_repo()
        files = []
        
        for root, dirs, filenames in os.walk(repo_path):
            # Skip .git directory
            if '.git' in root:
                continue
                
            rel_root = os.path.relpath(root, repo_path)
            if rel_root == '.':
                rel_root = ''
                
            for filename in filenames:
                filepath = os.path.join(rel_root, filename) if rel_root else filename
                full_path = os.path.join(root, filename)
                try:
                    file_size = os.path.getsize(full_path)
                except:
                    file_size = 0
                    
                files.append({
                    'name': filename,
                    'path': filepath,
                    'size': file_size,
                    'is_dir': False
                })
            
            # Add directories
            for dirname in dirs:
                if dirname != '.git':
                    dirpath = os.path.join(rel_root, dirname) if rel_root else dirname
                    files.append({
                        'name': dirname,
                        'path': dirpath,
                        'size': 0,
                        'is_dir': True
                    })
        
        return sorted(files, key=lambda x: (not x['is_dir'], x['name']))
    
    def get_file_content(self, filepath):
        """Get file content"""
        repo_path = self._setup_temp_repo()
        full_path = os.path.join(repo_path, filepath)
        
        if os.path.exists(full_path):
            try:
                with open(full_path, 'r', encoding='utf-8') as f:
                    return f.read()
            except UnicodeDecodeError:
                # Try binary read for non-text files
                with open(full_path, 'rb') as f:
                    return f.read().decode('utf-8', errors='ignore')
        return ""
    
    def get_file_type(self, filepath):
        """Determine file type for syntax highlighting"""
        text_extensions = {'.txt', '.md', '.py', '.js', '.html', '.css', '.json', '.xml', '.yml', '.yaml', '.sh', '.sql'}
        code_extensions = {'.py', '.js', '.html', '.css', '.java', '.cpp', '.c', '.h', '.go', '.rs', '.php'}
        
        _, ext = os.path.splitext(filepath.lower())
        
        if ext in code_extensions:
            return 'code'
        elif ext in text_extensions:
            return 'text'
        else:
            return 'binary'
    
    def __del__(self):
        """Cleanup temporary files"""
        if hasattr(self, 'temp_repo') and self.temp_repo and os.path.exists(self.temp_repo):
            shutil.rmtree(self.temp_repo, ignore_errors=True)
