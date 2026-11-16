from smartdoc.naming import proposed_name

def test_pan_short_name():
    assert proposed_name('Government-issued Document','PAN card','','scan.jpg')=='Pan.jpg'

def test_no_invented_lab_details():
    assert proposed_name('Medical Record','','','scan.pdf')=='Medical_Record.pdf'

def test_unrecognized():
    assert proposed_name('uncategorizable input','','','scan.png')=='N/A'

def test_sanitize_suggested():
    assert proposed_name('Bank Statement','','HDFC:Oct/26','scan.pdf')=='HDFCOct26.pdf'
