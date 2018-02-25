from ctypes import *
import math
import random

def sample(probs):
    s = sum(probs)
    probs = [a/s for a in probs]
    r = random.uniform(0, 1)
    for i in range(len(probs)):
        r = r - probs[i]
        if r <= 0:
            return i
    return len(probs)-1

def c_array(ctype, values):
    arr = (ctype*len(values))()
    arr[:] = values
    return arr

class BOX(Structure):
    _fields_ = [("x", c_float),
                ("y", c_float),
                ("w", c_float),
                ("h", c_float)]

class IMAGE(Structure):
    _fields_ = [("w", c_int),
                ("h", c_int),
                ("c", c_int),
                ("data", POINTER(c_float))]

class METADATA(Structure):
    _fields_ = [("classes", c_int),
                ("names", POINTER(c_char_p))]

    

#lib = CDLL("/home/pjreddie/documents/darknet/libdarknet.so", RTLD_GLOBAL)
lib = CDLL("libdarknet.so", RTLD_GLOBAL)
lib.network_width.argtypes = [c_void_p]
lib.network_width.restype = c_int
lib.network_height.argtypes = [c_void_p]
lib.network_height.restype = c_int

predict = lib.network_predict
predict.argtypes = [c_void_p, POINTER(c_float)]
predict.restype = POINTER(c_float)

set_gpu = lib.cuda_set_device
set_gpu.argtypes = [c_int]

make_image = lib.make_image
make_image.argtypes = [c_int, c_int, c_int]
make_image.restype = IMAGE

make_boxes = lib.make_boxes
make_boxes.argtypes = [c_void_p]
make_boxes.restype = POINTER(BOX)

free_ptrs = lib.free_ptrs
free_ptrs.argtypes = [POINTER(c_void_p), c_int]

num_boxes = lib.num_boxes
num_boxes.argtypes = [c_void_p]
num_boxes.restype = c_int

make_probs = lib.make_probs
make_probs.argtypes = [c_void_p]
make_probs.restype = POINTER(POINTER(c_float))

detect = lib.network_predict
detect.argtypes = [c_void_p, IMAGE, c_float, c_float, c_float, POINTER(BOX), POINTER(POINTER(c_float))]

reset_rnn = lib.reset_rnn
reset_rnn.argtypes = [c_void_p]

load_net = lib.load_network
load_net.argtypes = [c_char_p, c_char_p, c_int]
load_net.restype = c_void_p

free_image = lib.free_image
free_image.argtypes = [IMAGE]

letterbox_image = lib.letterbox_image
letterbox_image.argtypes = [IMAGE, c_int, c_int]
letterbox_image.restype = IMAGE

load_meta = lib.get_metadata
lib.get_metadata.argtypes = [c_char_p]
lib.get_metadata.restype = METADATA

load_image = lib.load_image_color
load_image.argtypes = [c_char_p, c_int, c_int]
load_image.restype = IMAGE

rgbgr_image = lib.rgbgr_image
rgbgr_image.argtypes = [IMAGE]

predict_image = lib.network_predict_image
predict_image.argtypes = [c_void_p, IMAGE]
predict_image.restype = POINTER(c_float)

network_detect = lib.network_detect
network_detect.argtypes = [c_void_p, IMAGE, c_float, c_float, c_float, POINTER(BOX), POINTER(POINTER(c_float))]

def classify(net, meta, im):
    out = predict_image(net, im)
    res = []
    for i in range(meta.classes):
        res.append((meta.names[i], out[i]))
    res = sorted(res, key=lambda x: -x[1])
    return res

def detect(net, meta, image, thresh=.5, hier_thresh=.5, nms=.45):
    im = load_image(image, 0, 0)
    boxes = make_boxes(net)
    probs = make_probs(net)
    num =   num_boxes(net)
    network_detect(net, im, thresh, hier_thresh, nms, boxes, probs)
    res = []
    for j in range(num):
        for i in range(meta.classes):
            if probs[j][i] > 0:
                res.append((meta.names[i], probs[j][i], (boxes[j].x, boxes[j].y, boxes[j].w, boxes[j].h)))
    res = sorted(res, key=lambda x: -x[1])
    free_image(im)
    free_ptrs(cast(probs, POINTER(c_void_p)), num)
    return res
   
    
if __name__ == "__main__":
    #net = load_net("cfg/densenet201.cfg", "/home/pjreddie/trained/densenet201.weights", 0)
    #im = load_image("data/wolf.jpg", 0, 0)
    #meta = load_meta("cfg/imagenet1k.data")
    #r = classify(net, meta, im)
    #print r[:10]
    # net = load_net("cfg/yolo.cfg", "yolo.weights", 0)
    # meta = load_meta("cfg/coco.data")
    # r = detect(net, meta, "/Users/The0s/Downloads/IMG_8273.JPG", thresh=.2)    
    # print r
    # s={}
    # for d in r:
    #     ob = d[0]
    #     if ob not in s:
    #         s[ob] = 1
    #     else:
    #         s[ob] = s[ob]+1
    # print s


    import time
    import pyrebase
    current_milli_time = lambda: int(round(time.time() * 1000))
    config = {
      "apiKey": "AIzaSyA7ndgXlwJ40VxblATilxbzcBv2JzAMjhU",
      "authDomain": "applepeachandroid.firebaseapp.com",
      "databaseURL": "https://applepeachandroid.firebaseio.com/",
      "storageBucket": "applepeachandroid.appspot.com",
      "serviceAccount": "/Users/The0s/Downloads/service.json"
    }

    firebase = pyrebase.initialize_app(config)
    # Get a reference to the auth service
    auth = firebase.auth()
    # Log the user in
    user = auth.sign_in_with_email_and_password("admin@gmail.com", "admin123")
    # Get a reference to the database service
    db = firebase.database()
  
    # Import gcloud
    from google.cloud import storage
    import cv2
    import numpy as np
    from PIL import Image
    
    net = load_net("cfg/yolo.cfg", "yolo.weights", 0)
    meta = load_meta("cfg/coco.data")

    #net = load_net("cfg/tiny-yolo-voc.cfg", "tiny-yolo-voc.weights", 0)
    #meta = load_meta("cfg/voc.data")

    while(1):
        try:
            # Enable Storage
            client = storage.Client()
            # Reference an existing bucket.
            bucket = client.get_bucket('applepeachandroid.appspot.com')

            # # # Upload a local file to a new file to be created in your bucket.
            # zebraBlob = bucket.get_blob('zebra.jpg')
            # zebraBlob.upload_from_filename(filename='/photos/zoo/zebra.jpg')

            # Download a file from your bucket.
            giraffeBlob = bucket.get_blob('droneImages/ImageMTF.jpg')
            # giraffeBlob = bucket.get_blob('person.jpg')
            if giraffeBlob:
                img_str = giraffeBlob.download_as_string()

                # CV2
                nparr = np.fromstring(img_str, np.uint8)
                img_np = cv2.imdecode(nparr, cv2.IMREAD_COLOR) # cv2.IMREAD_COLOR in OpenCV 3.1
                #cv2.imshow('image',img_np)
                #cv2.waitKey(1)
                cv2.imwrite('test.png',img_np)
                r = detect(net, meta, 'test.png', thresh=.2)    
                print r
                s={}
                for d in r:
                    ob = d[0]
                    if ob not in s:
                        s[ob] = 1
                    else:
                        s[ob] = s[ob]+1
                    rec=d[2]
                    p = (int(rec[0]),int(rec[1]))
                    p1 = (int(rec[0]-(rec[2]/2)),int(rec[1]-(rec[3]/2)))
                    p2 = (int(rec[0]+(rec[2]/2)),int(rec[1]+(rec[3]/2)))
                    cv2.rectangle(img_np,p1,p2,(0,255,0),3)
                    font = cv2.FONT_HERSHEY_SIMPLEX
                    cv2.putText(img_np,ob,p2, font, 0.4,(0,255,255),1,cv2.LINE_AA)
                print s
                cv2.imshow('image',img_np)

                s['timestamp'] = current_milli_time() 

                # Pass the user's idToken to the push method    
                results = db.child("droneStats").child("NissiBeach").update(s,user['idToken'])  
                cv2.waitKey(1)
        except Exception as e:
            print(e)
    cv2.destroyAllWindows()

