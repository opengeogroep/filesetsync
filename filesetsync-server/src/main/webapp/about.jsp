<%--
Copyright (C) 2013 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>About</title>
    </head>
    <body>
        <h1>filesetsync-admin</h1>
        <c:set var="version" value="${project.version}"/>
        <table>
            
            <tr>
                <td><b>Version:</b></td>
                <td>
                    <c:choose>
                        <c:when test="${fn:contains(version,'SNAPSHOT')}">
                            ${project.version}-${git.commit.id.abbrev}
                        </c:when>
                        <c:otherwise>
                            ${project.version}
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
            <tr>
                <td><b>Build time:</b></td>
                <td>${git.build.time}</td>
            </tr>
            <tr>
                <td><b>Built by:</b></td>
                <td>${git.build.user.name}</td>
            </tr>
            <tr>
                <td colspan="2">
                    <center><b>Git details</b></center>
                </td>
            </tr>
            <tr>
                <td><b>Git branch:</b></td>
                <td>${git.branch}</td>
            </tr>
            <tr>
                <td><b>Git remote url</b></td>
                <td>${git.remote.origin.url}</td>
            </tr>
            <tr>
                <td><b>Git commit abbrev id:</b></td>
                <td>${git.commit.id.abbrev}</td>
            </tr>
            <tr>
                <td><b>Git commit full id:</b></td>
                <td>${git.commit.id}</td>
            </tr>
            <tr>
                <td><b>Git commit time:</b></td>
                <td>${git.commit.time}</td>
            </tr>
        </table>
    </body>
</html>
