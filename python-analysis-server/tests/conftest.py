"""
[STEP 10] pytest 공통 픽스처 및 설정

실행 방법:
    cd python-analysis-server
    pytest tests/ -v
"""
import sys
import os

# python-analysis-server 루트를 sys.path에 추가하여 app 모듈 임포트 가능하게 함
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
