import httplib, urllib, re, mimetools, StringIO, multifile
import xml.etree.ElementTree as ET
from string import lowercase

def extract_mime_part_matching(stream, mimetype):
	mfile = multifile.MultiFile(stream)
	mfile.push("_BOUNDRY_02468_STRING_13579_XXXXXXX")
	
	while 1:	
		submsg = mimetools.Message(mfile)
		data = StringIO.StringIO()
		mimetools.decode(mfile, data, submsg.getencoding())

		if (not mfile.next()) or submsg.gettype() == mimetype : break

	mfile.pop()

	return data.getvalue()

class camera:
	
	def __init__(self, address):
		self.address = address
		
	def open(self):
		headers = {"Content-type": "text/xml"}
		conn = httplib.HTTPConnection(self.address, 8615)
		conn.request("POST", "/MobileConnectedCamera/UsecaseStatus?Name=ObjectPull&MajorVersion=1&MinorVersion=0", '<?xml version="1.0"?><ParamSet xmlns="urn:schemas-canon-com:service:MobileConnectedCameraService:1"><Status>Run</Status></ParamSet>', headers)
		conn.close()
		
	def close(self):
		headers = {"Content-type": "text/xml"}
		conn = httplib.HTTPConnection(self.address, 8615)
		conn.request("POST", "/MobileConnectedCamera/UsecaseStatus?Name=ObjectPull&MajorVersion=1&MinorVersion=0", '<?xml version="1.0"?><ParamSet xmlns="urn:schemas-canon-com:service:MobileConnectedCameraService:1"><Status>Stop</Status></ParamSet>', headers)
		conn.close()
		
	def getList(self):
		objects = {}
		totalNum = 2
		startIndex = 1
		while(startIndex < totalNum):
			params = urllib.urlencode({"StartIndex" : startIndex , "MaxNum" : 2147483647 , "ObjType" : "ALL"})
			conn = httplib.HTTPConnection(self.address, 8615)
			conn.request("GET", "/MobileConnectedCamera/ObjIDList?" + params)
			response = conn.getresponse()
			data = response.read()
			conn.close()
		
			data = re.sub(' xmlns="[^"]+"', '', data, count=1)
			root = ET.fromstring(data)
		
			totalNum = int(root.find('TotalNum').text)
			answerCount = int(root.find('ListCount').text)
		
			for i in range(1, answerCount):
				obj = MediaObj()
				obj.addr = self.address
				obj.id = root.find('ObjIDList-%d' % i).text
				obj.type = root.find('ObjTypeList-%d' % i).text		
				objects[obj.id] = obj
				
			startIndex += answerCount
			
		return objects

class MediaObj:
	def getMeta(self, responseMime):
		data = extract_mime_part_matching(responseMime, 'text/xml')

		data = re.sub(' xmlns="[^"]+"', '', data, count=1)
	
		root = ET.fromstring(data)
		return [int(root.find('TotalSize').text), int(root.find('DataSize').text)]

	def download(self, destination):
		offset = 0
		totalSize = 1
		while(offset < totalSize):
			params = urllib.urlencode({"ObjID" : self.id , "ObjType" : self.type, "Offset" : offset})
			conn = httplib.HTTPConnection(self.addr, 8615)
			conn.request("GET", "/MobileConnectedCamera/ObjData?" + params)
			response = conn.getresponse()
			
			respData = response.read()
			
			#Write respose data in a temporary file 
			tmpFile = open('tmpFile', 'w+b')
			tmpFile.write(respData)
			tmpFile.seek(0, 0)
			#Decode media metadata 
			props = self.getMeta(tmpFile)
			
			#Decode media data content
			data = extract_mime_part_matching(tmpFile, 'application/octet-stream')
			
			#Append media data to destination file
			destination.write(data)
			
			
			tmpFile.close()
			
			offset += props[1]
			totalSize = props[0]
			
			print 'Downloading file %s.%s: %s/%s' % (self.id, self.type, offset, totalSize)

def main():

	cam = camera("192.168.10.14")
	
	cam.open()
	
	objects = cam.getList()
	
	
	obj = objects['26490112']
	file_name = 'foto/' + obj.id + '.' + obj.type.lower()
	f = open(file_name, 'wb')
	obj.download(f)
	f.close()
	
	cam.close()

if __name__ == '__main__':
	main()