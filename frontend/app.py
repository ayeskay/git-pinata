# app.py
import streamlit as st
import requests
import tempfile
import os
import shutil
from pinata_client import PinataClient
from git_bundle_parser import GitBundleParser

# Initialize
st.set_page_config(
    page_title="IPFS Git Viewer",
    page_icon="📁",
    layout="wide"
)

# Session state
if 'repos' not in st.session_state:
    st.session_state.repos = []
if 'current_repo' not in st.session_state:
    st.session_state.current_repo = None
if 'current_path' not in st.session_state:
    st.session_state.current_path = ""

# Initialize clients
pinata = PinataClient()

def display_repository(repo):
    """Display repository contents"""
    cid = repo['cid']
    repo_name = repo.get('name', f"Repo {cid[:8]}...")
    
    st.title(f"Repository: {repo_name}")
    
    # Repository info bar
    col1, col2, col3 = st.columns([2, 1, 1])
    with col1:
        st.markdown(f"**CID:** `{cid}`")
    with col2:
        # Clone command in a copyable code box
        clone_command = f"git-pinata clone {cid} {repo_name}"
        st.markdown("**Clone Command:**")
        st.code(clone_command, language="bash")
    with col3:
        # Download button
        try:
            bundle_data = pinata.get_file(cid)
            st.download_button(
                label="📥 Download Bundle",
                data=bundle_data,
                file_name=f"{repo_name}.bundle",
                mime="application/octet-stream"
            )
        except Exception as e:
            st.error(f"Download failed: {str(e)}")
    
    st.markdown("---")
    
    # Fetch and parse bundle
    try:
        with st.spinner("Loading repository..."):
            bundle_data = pinata.get_file(cid)
            
            with tempfile.NamedTemporaryFile(suffix='.bundle', delete=False) as f:
                f.write(bundle_data)
                bundle_path = f.name
            
            parser = GitBundleParser(bundle_path)
            files = parser.get_file_tree()
            
            # Clean up
            os.unlink(bundle_path)
            
            # Filter by current path
            if st.session_state.current_path:
                files = [f for f in files if f['path'].startswith(st.session_state.current_path)]
                # Show path breadcrumbs
                path_parts = st.session_state.current_path.rstrip('/').split('/')
                breadcrumbs = []
                current_breadcrumb = ""
                for part in path_parts:
                    if part:
                        current_breadcrumb += part + "/"
                        breadcrumbs.append(current_breadcrumb)
                
                st.markdown("**Path:** 🏠 / " + " / ".join([
                    f"[{part}](?path={breadcrumb})" 
                    for part, breadcrumb in zip(path_parts[:-1], breadcrumbs)
                ]))
                
                if st.button("⬆️ Go Up"):
                    parent_path = "/".join(path_parts[:-1])
                    if parent_path:
                        st.session_state.current_path = parent_path + "/"
                    else:
                        st.session_state.current_path = ""
                    st.rerun()
            else:
                # Show root files only
                files = [f for f in files if '/' not in f['path'] or f['path'].count('/') == 0]
            
            # Display files
            display_files(files, cid, repo_name)
            
    except Exception as e:
        st.error(f"Error loading repository: {str(e)}")
        st.info("Make sure the CID is correct and the file is accessible via Pinata.")

def display_files(files, cid, repo_name):
    """Display files in a table format"""
    if not files:
        st.info("No files found in this directory.")
        return
    
    # Separate directories and files
    directories = [f for f in files if f['is_dir']]
    regular_files = [f for f in files if not f['is_dir']]
    
    # Display directories first
    if directories:
        st.subheader("📁 Directories")
        cols = st.columns(4)
        for i, file in enumerate(directories):
            with cols[i % 4]:
                if st.button(f"📁 {file['name']}", key=f"dir_{file['path']}"):
                    st.session_state.current_path = file['path'] + "/"
                    st.rerun()
    
    # Display files
    if regular_files:
        st.subheader("📄 Files")
        
        # Create table data
        table_data = []
        for file in regular_files:
            # Get relative path for display
            display_name = file['name']
            if st.session_state.current_path:
                relative_path = file['path'][len(st.session_state.current_path):]
            else:
                relative_path = file['path']
            
            table_data.append({
                "Name": display_name,
                "Size": f"{file['size']} bytes",
                "Actions": file['path']  # Store full path for actions
            })
        
        # Display as dataframe with custom styling
        import pandas as pd
        df = pd.DataFrame(table_data)
        
        # Show table
        st.dataframe(df, use_container_width=True, hide_index=True)
        
        # Action buttons for each file (outside dataframe)
        st.subheader("File Actions")
        for file in regular_files:
            col1, col2, col3 = st.columns([3, 1, 1])
            with col1:
                st.write(f"📄 {file['name']}")
            with col2:
                if st.button("👁️ View", key=f"view_{file['path']}"):
                    st.session_state.viewing_file = file['path']
                    display_file_content(file['path'], cid, repo_name)
            with col3:
                # Download button for individual file
                try:
                    bundle_data = pinata.get_file(cid)
                    with tempfile.NamedTemporaryFile(suffix='.bundle', delete=False) as f:
                        f.write(bundle_data)
                        bundle_path = f.name
                    
                    parser = GitBundleParser(bundle_path)
                    file_content = parser.get_file_content(file['path'])
                    os.unlink(bundle_path)
                    
                    st.download_button(
                        label="💾",
                        data=file_content,
                        file_name=file['name'],
                        key=f"dl_{file['path']}",
                        help="Download this file"
                    )
                except:
                    st.button("💾", key=f"dl_{file['path']}", disabled=True, help="File not available")
    else:
        st.info("No files in this directory.")

def display_file_content(filepath, cid, repo_name):
    """Display file content with syntax highlighting"""
    try:
        with st.spinner(f"Loading {filepath}..."):
            bundle_data = pinata.get_file(cid)
            
            with tempfile.NamedTemporaryFile(suffix='.bundle', delete=False) as f:
                f.write(bundle_data)
                bundle_path = f.name
            
            parser = GitBundleParser(bundle_path)
            file_content = parser.get_file_content(filepath)
            file_type = parser.get_file_type(filepath)
            
            os.unlink(bundle_path)
            
            st.subheader(f"📄 {filepath}")
            
            # File actions
            col1, col2 = st.columns([1, 1])
            with col1:
                if st.button("⬅️ Back to Repository"):
                    if 'viewing_file' in st.session_state:
                        del st.session_state.viewing_file
                    st.rerun()
            
            # Display content based on type
            if file_content and file_type in ['text', 'code']:
                # Try to detect language for syntax highlighting
                extension = filepath.split('.')[-1].lower() if '.' in filepath else ''
                
                # Show in code block with language
                if extension in ['py', 'js', 'html', 'css', 'java', 'cpp', 'c', 'h']:
                    st.code(file_content, language=extension)
                elif extension in ['md']:
                    st.markdown(file_content)
                elif extension in ['json']:
                    st.json(file_content)
                else:
                    st.text_area("Content", value=file_content, height=400, key="file_content")
            elif file_content:
                st.info("This file type cannot be displayed directly.")
                st.download_button(
                    label="📥 Download File",
                    data=file_content,
                    file_name=os.path.basename(filepath),
                    mime="application/octet-stream"
                )
            else:
                st.info("File is empty or could not be read.")
                
    except Exception as e:
        st.error(f"Error reading file: {str(e)}")

# Sidebar
with st.sidebar:
    st.title("📁 IPFS Git Viewer")
    
    # Repository management
    st.subheader("Repository Management")
    
    # Add repository by CID
    cid_input = st.text_input("Add Repository by CID", placeholder="Qm...")
    repo_name_input = st.text_input("Repository Name (optional)", placeholder="my-awesome-repo")

    if st.button("Add Repository") and cid_input:
        # Validate CID format (basic check)
        if cid_input.startswith('Qm') and len(cid_input) > 30:
            new_repo = {
                'cid': cid_input,
                'name': repo_name_input if repo_name_input else f"Repo {cid_input[:8]}...",
                'date': "Just added"
            }
            st.session_state.repos.append(new_repo)
            st.success("Repository added!")
        else:
            st.error("Invalid CID format")
    
    # List repositories
    st.subheader("Your Repositories")
    if st.session_state.repos:
        for i, repo in enumerate(st.session_state.repos):
            col1, col2 = st.columns([3, 1])
            with col1:
                if st.button(f"📁 {repo['name']}", key=f"repo_{i}"):
                    st.session_state.current_repo = repo
                    st.session_state.current_path = ""
            with col2:
                if st.button("🗑️", key=f"del_{i}"):
                    st.session_state.repos.pop(i)
                    st.rerun()
    else:
        st.info("No repositories added yet. Add one using the CID above.")

# Main content
if not st.session_state.repos:
    st.title("Welcome to IPFS Git Viewer")
    st.write("This tool lets you browse Git repositories stored on IPFS via Pinata.")
    st.write("### Quick Start:")
    st.write("1. Add a repository by entering its IPFS CID in the sidebar")
    st.write("2. Browse files and folders")
    st.write("3. View file contents or download the complete bundle")
    
    st.info("💡 Tip: After running `git-pinata.sh`, you'll get a CID. Paste that CID in the sidebar to view your repository!")
    
else:
    if st.session_state.current_repo:
        display_repository(st.session_state.current_repo)
    else:
        # Show first repo by default
        display_repository(st.session_state.repos[0])

# Clone instructions page
if st.sidebar.button("📋 Clone Instructions"):
    st.title("Clone Repository Instructions")
    
    if st.session_state.current_repo:
        cid = st.session_state.current_repo['cid']
        repo_name = st.session_state.current_repo.get('name', 'my-repo')
        clone_url = f"https://gateway.pinata.cloud/ipfs/{cid}"
        
        st.subheader("Using git-pinata CLI (Recommended)")
        st.markdown("Make sure you have the `git-pinata` script installed:")
        st.code(f"git-pinata clone {cid} {repo_name}", language="bash")
        
        st.subheader("Manual Method")
        st.code(f"""
# Download the bundle
curl -o {repo_name}.bundle "{clone_url}"

# Clone from bundle
git clone {repo_name}.bundle {repo_name}
cd {repo_name}
git reset --hard
        """, language="bash")
        
        st.subheader("Direct IPFS Access")
        st.write(f"**IPFS CID:** `{cid}`")
        st.write(f"**Gateway URL:** {clone_url}")
        
        st.subheader("Using IPFS Client")
        st.code(f"""
ipfs get {cid}
# Then extract and use as Git repository
        """, language="bash")
        
        # Download button
        try:
            bundle_data = pinata.get_file(cid)
            st.download_button(
                label="📥 Download Bundle Now",
                data=bundle_data,
                file_name=f"{repo_name}.bundle",
                mime="application/octet-stream",
                key="clone_download"
            )
        except Exception as e:
            st.error(f"Download failed: {str(e)}")
    else:
        st.info("Please select a repository first.")
