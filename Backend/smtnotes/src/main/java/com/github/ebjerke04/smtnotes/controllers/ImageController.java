package com.github.ebjerke04.smtnotes.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMethod;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.springframework.http.ResponseEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600, 
    allowedHeaders = "*", 
    exposedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class ImageController {

    private List<StoredImage> imageStore = new ArrayList<>();
    private final Tesseract tesseract;

    public ImageController() {
        tesseract = new Tesseract();
        tesseract.setDatapath("src/main/resources/tessdata");
    }
    
    private static class StoredImage {
        private byte[] imageData;
        private String fileName;
        private String contentType;
        private String extractedText;

        public StoredImage(byte[] imageData, String fileName, String contentType, String extractedText) {
            this.imageData = imageData;
            this.fileName = fileName;
            this.contentType = contentType;
            this.extractedText = extractedText;
        }

        public byte[] getImageData() {
            return imageData;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public String getExtractedText() {
            return extractedText;
        }
    }
    
    @PostMapping("/upload")
    public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }

            byte[] fileData = file.getBytes();
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            String extractedText = "";

            if (contentType.startsWith("application/pdf")) {
                try (PDDocument document = PDDocument.load(fileData)) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                        BufferedImage bufferedImage = renderer.renderImageWithDPI(pageIndex, 300);
                        
                        if (pageIndex == 0) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(bufferedImage, "PNG", baos);
                            fileData = baos.toByteArray();
                            contentType = "image/png";
                        }
                        
                        try {
                            extractedText += tesseract.doOCR(bufferedImage);
                        } catch (TesseractException e) {
                            System.err.println("OCR failed: " + e.getMessage());
                        }
                    }
                }
            } else if (contentType.startsWith("image/")) {
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(fileData));
                try {
                    extractedText = tesseract.doOCR(bufferedImage);
                } catch (TesseractException e) {
                    System.err.println("OCR failed: " + e.getMessage());
                }
            } else {
                return ResponseEntity.badRequest().body("Unsupported file type: " + contentType);
            }
            
            // Store the image in our list
            StoredImage storedImage = new StoredImage(fileData, fileName, contentType, extractedText);
            imageStore.add(storedImage);

            return ResponseEntity.ok("Image uploaded successfully: " + fileName);
        } catch (IOException error) {
            return ResponseEntity.internalServerError().body("Failed to upload image: " + error.getMessage());
        }
    }

    @GetMapping("/textinfo")
    public List<String> getTextFromImages() {
        List<String> imageTexts = new ArrayList<>();

        for (StoredImage image : imageStore) {
            imageTexts.add(image.getExtractedText());
        }

        return imageTexts;
    }

    @GetMapping("/view")
    public ResponseEntity<List<String>> getAllImageNames() {
        List<String> imageNames = imageStore.stream()
            .map(StoredImage::getFileName)
            .collect(Collectors.toList());
        return ResponseEntity.ok(imageNames);
    }

    @GetMapping("/image/{fileName}")
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) {
        return imageStore.stream()
            .filter(img -> img.getFileName().equals(fileName))
            .findFirst()
            .map(img -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .body(img.getImageData()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/gallery")
    public ResponseEntity<String> getGalleryHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='background-color: #f0f0f0; padding: 20px;'>");
        html.append("<h1 style='color: #333; text-align: center;'>Image Gallery</h1>");
        html.append("<div style='display: flex; flex-wrap: wrap; gap: 20px; justify-content: center;'>");
        
        for (StoredImage image : imageStore) {
            html.append(String.format(
                "<div style='background: white; padding: 10px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>" +
                "<img src='/image/%s' style='max-width: 300px; max-height: 300px; object-fit: contain;' />" +
                "<p style='text-align: center; margin-top: 10px;'>%s</p>" +
                "</div>",
                image.getFileName(), image.getFileName()
            ));
        }
        
        html.append("</div></body></html>");
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html.toString());
    }
}
