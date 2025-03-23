// Function to handle file upload
function uploadImage(imageFile) {
    // Create FormData object to send the file
    const formData = new FormData();
    formData.append('file', imageFile);

    // Make the POST request
    fetch('http://localhost:8080/upload', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        return response.text();
    })
    .then(data => {
        console.log('Success:', data);
    })
    .catch(error => {
        console.error('Error:', error);
    });
}

// Example HTML form to test the upload
// <input type="file" id="imageInput" accept="image/*">
// <button onclick="handleUpload()">Upload Image</button>

function handleUpload() {
    const fileInput = document.getElementById('imageInput');
    const file = fileInput.files[0];
    
    if (file) {
        uploadImage(file);
    } else {
        console.error('No file selected');
    }
}