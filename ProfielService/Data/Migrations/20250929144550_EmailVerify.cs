using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace ProfielService.Data.Migrations
{
    /// <inheritdoc />
    public partial class EmailVerify : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "EmailVerified",
                table: "ondernemingen",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "EmailVerified",
                table: "ondernemingen");
        }
    }
}
