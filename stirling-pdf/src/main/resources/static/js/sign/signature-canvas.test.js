/**
 * @jest-environment @stryker-mutator/jest-runner/jest-env/jsdom
 */

// Mocks
const mockSignaturePadInstance = {
  toData: jest.fn(),
  fromData: jest.fn(),
  clear: jest.fn(),
  isEmpty: jest.fn(),
  addEventListener: jest.fn(),
};
global.SignaturePad = jest.fn().mockImplementation(() => mockSignaturePadInstance);
global.DraggableUtils = { createDraggableCanvasFromUrl: jest.fn() };

// Mock browser APIs
Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: jest.fn().mockImplementation((query) => ({ matches: false })),
});
Object.defineProperty(window, "devicePixelRatio", {
  writable: true,
  value: 1,
});

const mockIntersectionObserver = { observe: jest.fn(), disconnect: jest.fn(), _trigger: jest.fn() };
global.IntersectionObserver = jest.fn((callback) => {
  mockIntersectionObserver._trigger = (entries) => callback(entries);
  return mockIntersectionObserver;
});

const mockResizeObserver = { observe: jest.fn(), disconnect: jest.fn(), _trigger: jest.fn() };
global.ResizeObserver = jest.fn((callback) => {
  mockResizeObserver._trigger = () => callback();
  return mockResizeObserver;
});

// Require the script's functions
const {
  init,
  undoDraw,
  redoDraw,
  addDraggableFromPad,
  getCroppedCanvasDataUrl,
  resizeCanvas,
} = require("./signature-canvas.js");

// Test Suite
describe("Signature Canvas", () => {
  let undoButton, redoButton, signaturePadCanvas;
  let cleanup;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    document.body.innerHTML = `
      <canvas id="drawing-pad-canvas"></canvas>
      <button id="signature-undo-button"></button>
      <button id="signature-redo-button"></button>
    `;
    undoButton = document.getElementById("signature-undo-button");
    redoButton = document.getElementById("signature-redo-button");
    signaturePadCanvas = document.getElementById("drawing-pad-canvas");
    undoButton.click = jest.fn();
    redoButton.click = jest.fn();

    // Define offsetWidth for the resizeCanvas test
    Object.defineProperty(signaturePadCanvas, "offsetWidth", { configurable: true, value: 100 });
    Object.defineProperty(signaturePadCanvas, "offsetHeight", { configurable: true, value: 100 });

    cleanup = init();
  });

  afterEach(() => {
    if (cleanup) {
      cleanup();
    }
    jest.useRealTimers();
  });

  describe("Undo/Redo Logic", () => {
    it("should undo a draw action", () => {
      mockSignaturePadInstance.toData.mockReturnValue([{}, {}]);
      undoDraw();
      expect(mockSignaturePadInstance.fromData).toHaveBeenCalledWith([{}]);
    });

    it("should not redo if there is no undo data", () => {
      const endStrokeCallback = mockSignaturePadInstance.addEventListener.mock.calls.find(
        (call) => call[0] === "endStroke"
      )[1];
      endStrokeCallback();

      redoDraw();
      expect(mockSignaturePadInstance.fromData).not.toHaveBeenCalled();
    });
  });

  describe("Canvas and Image Utilities", () => {
    it("should add a draggable if pad is not empty", () => {
      mockSignaturePadInstance.isEmpty.mockReturnValue(false);
      addDraggableFromPad();
      expect(DraggableUtils.createDraggableCanvasFromUrl).toHaveBeenCalled();
    });

    it("should return a data URL for a non-empty canvas", () => {
      // Create a mock context that is ONLY used for this test
      const mockContext = {
        getImageData: jest.fn().mockImplementation((x, y, w, h) => {
          // Return fake pixel data with one non-transparent pixel
          const fakeImageData = { data: new Uint8ClampedArray(w * h * 4).fill(0) };
          fakeImageData.data[3] = 255; // Set alpha for the first pixel
          return fakeImageData;
        }),
        putImageData: jest.fn(),
      };
      // Spy on getContext and use our mock just for this call
      const getContextSpy = jest.spyOn(signaturePadCanvas, "getContext").mockImplementation(() => mockContext);

      const dataUrl = getCroppedCanvasDataUrl(signaturePadCanvas);
      expect(dataUrl).toContain("data:image/png");

      // Restore the original getContext implementation for other tests
      getContextSpy.mockRestore();
    });

    it("should return null for a completely empty canvas", () => {
      // FIX #1: Mock getContext to return a context with empty image data
      const mockContext = {
        getImageData: jest.fn().mockReturnValue({ data: new Uint8ClampedArray(400).fill(0) }),
      };
      const getContextSpy = jest.spyOn(signaturePadCanvas, "getContext").mockImplementation(() => mockContext);

      const dataUrl = getCroppedCanvasDataUrl(signaturePadCanvas);
      expect(dataUrl).toBeNull();

      getContextSpy.mockRestore();
    });
  });
});
