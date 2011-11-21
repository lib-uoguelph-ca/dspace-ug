<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
                xmlns:dri="http://di.tamu.edu/DRI/1.0/"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<!-- Add an "About the Atrium" link to the header -->

<xsl:template match="*">
    <xsl:copy>
        <xsl:copy-of select="@*"/>
        <xsl:apply-templates/>
    </xsl:copy>
</xsl:template>

<xsl:template match="dri:pageMeta">
    <xsl:copy>
        <xsl:copy-of select="@*"/>
        <dri:trail target="/">Home</dri:trail>
        <xsl:apply-templates/>
    </xsl:copy>
</xsl:template>

</xsl:stylesheet>
